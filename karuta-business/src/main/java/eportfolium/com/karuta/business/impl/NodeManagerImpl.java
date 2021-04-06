/* =======================================================
	Copyright 2020 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

package eportfolium.com.karuta.business.impl;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import com.fasterxml.jackson.core.JsonProcessingException;

import eportfolium.com.karuta.business.security.*;
import eportfolium.com.karuta.consumer.repositories.*;
import eportfolium.com.karuta.document.*;
import eportfolium.com.karuta.model.bean.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import eportfolium.com.karuta.business.contract.GroupManager;
import eportfolium.com.karuta.business.contract.NodeManager;
import eportfolium.com.karuta.business.contract.PortfolioManager;
import eportfolium.com.karuta.business.contract.ResourceManager;
import eportfolium.com.karuta.business.contract.SecurityManager;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.model.exception.GenericBusinessException;
import eportfolium.com.karuta.util.InMemoryCache;

@Service
@Transactional
public class NodeManagerImpl extends BaseManagerImpl implements NodeManager {

	static private final Logger log = LoggerFactory.getLogger(NodeManagerImpl.class);

	@Autowired
	private GroupManager groupManager;

	@Autowired
	private PortfolioManager portfolioManager;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private SecurityManager securityManager;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private NodeRepository nodeRepository;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private GroupRightInfoRepository groupRightInfoRepository;

	@Autowired
	private GroupInfoRepository groupInfoRepository;

	@Autowired
	private GroupUserRepository groupUserRepository;

	//// Portfolio -> parent id -> list of node (0: parent, 1+: child)
	private InMemoryCache<UUID, Map<UUID, List<Node>>> cachedNodes = new InMemoryCache<>(600, 1500, 6);

	@Override
	@CanReadOrPublic
	public String getNode(@P("id") UUID nodeId, Long userId, Integer cutoff) throws JsonProcessingException {
		final GroupRights rights = getRights(userId, nodeId);

		List<Pair<Node, GroupRights>> nodes = getNodePerLevel(nodeId, userId, rights.getGroupRightInfo().getId(),
				cutoff);

		/// Node -> parent
		Map<UUID, Tree> entries = new HashMap<>();
		processQuery(nodes, entries, rights.getGroupRightInfo().getLabel());

		StringBuilder sb = new StringBuilder();
		
		/// Reconstruct functional tree
		Tree root = entries.get(nodeId);
		reconstructTree(sb, root.node, root, entries);

		return sb.toString();
	}

	@Override
	public Node writeNode(NodeDocument node, UUID portfolioId, Long userId, int ordrer, UUID forcedId,
						  Node parentNode, boolean rewriteId, Map<UUID, UUID> resolve, boolean parseRights)
			throws BusinessException, JsonProcessingException {

		if (node == null)
			return null;

		String code = "";

		Optional<ResourceDocument> resource = node.getResources()
			.stream()
			.filter(n -> "nodeRes".equals(n.getXsiType())).findFirst();

		if (resource.isPresent())
			code = resource.get().getCode();

		String semanticTag = null;

		final UUID nodeId = rewriteId ? node.getId() : (forcedId != null ? forcedId : UUID.randomUUID());

		// If we are dealing with the root of the tree, we need to ensure
		// that the node's type is "asmRoot".
		if (nodeId != null && node.getParent() != null) {
			if (!node.getType().equals("asmRoot") && portfolioId == null)
				throw new GenericBusinessException("Missing node with 'asmRoot' type");

			List<ResourceDocument> resourceDocuments = node.getResources();

			if (!resourceDocuments.isEmpty()) {
				code = resourceDocuments.get(0).getCode();
			}
		}

		if (parseRights && node.getMetadataWad() != null) {
			MetadataWadDocument metadataWad = node.getMetadataWad();

			BiConsumer<String, String> processRoles = (roles, right) -> {
				if (roles != null) {
					for (String role : roles.split(" ")) {
						groupManager.addGroupRights(role, nodeId, right, portfolioId, userId);
					}
				}
			};

			processRoles.accept(metadataWad.getSeenoderoles(), GroupRights.READ);
			processRoles.accept(metadataWad.getDelnoderoles(), GroupRights.DELETE);
			processRoles.accept(metadataWad.getEditnoderoles(), GroupRights.WRITE);
			processRoles.accept(metadataWad.getEditresroles(), GroupRights.WRITE);
			processRoles.accept(metadataWad.getSubmitroles(), GroupRights.SUBMIT);
			processRoles.accept(metadataWad.getShowtoroles(), GroupRights.NONE);

			if (metadataWad.getNotifyroles() != null) {
				groupManager.changeNotifyRoles(portfolioId, nodeId,
						metadataWad.getNotifyroles().replace(" ", ","));
			}

			groupManager.setPublicState(userId, portfolioId, node.getMetadata().getPublic());

		}


		if (node.getMetadata() != null) {
			MetadataDocument metadata = node.getMetadata();

			semanticTag = metadata.getSemantictag();
		}

		Node nodeEntity = add(node, nodeId, parentNode, semanticTag, code, ordrer, userId, portfolioId);

		if (resolve != null) // Mapping old id -> new id
			resolve.put(node.getId(), nodeEntity.getId());

		//// Insert resource associated with this node
		for (ResourceDocument resourceDocument : node.getResources()) {
			resourceManager.addResource(nodeEntity, resourceDocument, userId);
		}
		
		// Loop through children to go down in the tree.
		if (!node.getChildren().isEmpty()) {
			int k = 0;

			for (NodeDocument child : node.getChildren()) {
				UUID childId = null;

				if (!rewriteId)
					childId = UUID.randomUUID();

				writeNode(child, portfolioId, userId, k, childId, nodeEntity, rewriteId, resolve, parseRights);
				k++;
			}
		}

		if (parentNode != null)	// Update parent children list, if asmRoot, no parent
			updateNode(parentNode.getId());

		return nodeEntity;
	}

	@Override
	public NodeDocument getNode(UUID nodeId, boolean withChildren, String withChildrenOfXsiType, Long userId,
			String label, boolean checkSecurity) throws JsonProcessingException {
		if (checkSecurity) {
			GroupRights rights = getRights(userId, nodeId);

			// If the user doesn't have the right to see this node, we
			// check whether the public account has such right.
			if (!rights.isRead()) {
				Long publicId = credentialRepository.getPublicId();

				rights = groupRightsRepository.getPublicRightsByUserId(nodeId, publicId);
				if (!rights.isRead())
					return null;
			}
		}

		Optional<Node> nodeOptional = nodeRepository.findById(nodeId);

		if (!nodeOptional.isPresent())
			return null;

		Node node = nodeOptional.get();
		NodeDocument nodeDocument;

		if (node.getSharedNodeUuid() != null) {
			nodeDocument = getNode(node.getSharedNodeUuid(), true, null, userId,
					null, true);
		} else {
			nodeDocument = new NodeDocument(node);

			MetadataWadDocument metadataWad = MetadataWadDocument.from(node.getMetadataWad());
			MetadataEpmDocument metadataEpm = MetadataEpmDocument.from(node.getMetadataEpm());
			MetadataDocument metadataNode = MetadataDocument.from(node.getMetadata());

			nodeDocument.setCode(node.getCode());
			nodeDocument.setLabel(node.getLabel());
			nodeDocument.setDescription(node.getDescr());
			nodeDocument.setSemtag(node.getSemtag());

			List<ResourceDocument> resources = new ArrayList<>();

			if (node.getResResource() != null) {
				resources.add(new ResourceDocument(node.getResResource(), node));
			}

			if (node.getContextResource() != null) {
				resources.add(new ResourceDocument(node.getContextResource(), node));
			}

			if (node.getResource() != null) {
				resources.add(new ResourceDocument(node.getResource(), node));
			}

			nodeDocument.setMetadata(metadataNode);
			nodeDocument.setMetadataEpm(metadataEpm);
			nodeDocument.setMetadataWad(metadataWad);
			nodeDocument.setResources(resources);
		}

		if (withChildren || withChildrenOfXsiType != null) {
			if (StringUtils.isNotEmpty(node.getChildrenStr())) {
				List<UUID> uuids = Arrays.stream(node.getChildrenStr().split(","))
									.map(UUID::fromString)
									.collect(Collectors.toList());

				List<NodeDocument> children = new ArrayList<>();

				for (UUID uuid : uuids) {
					Node child = nodeRepository.findById(uuid).orElse(new Node());

					if (withChildrenOfXsiType == null
							|| withChildrenOfXsiType.equals(child.getXsiType())) {
						children.add(getNode(uuid, true, null, userId, null, true));
					}
				}

				nodeDocument.setChildren(children);
			}
		}

		return nodeDocument;
	}

	@Override
	public String getNodeWithXSL( UUID nodeUuid, String folder, String xslFile, String parameters, Long userId )
	{
		String xml;
		try {
			/// Preparing parameters for future need, format: "par1:par1val;par2:par2val;..."
			String[] table = parameters.split(";");
			int parSize = table.length;
			String param[] = new String[parSize];
			String paramVal[] = new String[parSize];
			for( int i=0; i<parSize; ++i )
			{
				String line = table[i];
				int var = line.indexOf(":");
				param[i] = line.substring(0, var);
				paramVal[i] = line.substring(var+1);
			}

			/// TODO: Test this more, should use getNode rather than having another output
			xml = getNode(nodeUuid, userId, null).toString();
			if( xml == null )
				return null;

    	String path = folder.substring(0,folder.lastIndexOf(File.separator, folder.length()-2)+1);
    	xslFile = path+xslFile;

			xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
					"<!DOCTYPE xsl:stylesheet [" +
					"<!ENTITY % lat1 PUBLIC \"-//W3C//ENTITIES Latin 1 for XHTML//EN\" \""+folder+"xhtml-lat1.ent\">" +
					"<!ENTITY % symbol PUBLIC \"-//W3C//ENTITIES Symbols for XHTML//EN\" \""+folder+"xhtml-symbol.ent\">" +
					"<!ENTITY % special PUBLIC \"-//W3C//ENTITIES Special for XHTML//EN\" \""+folder+"xhtml-special.ent\">" +
					"%lat1;" +
					"%symbol;" +
					"%special;" +
					"]>" + // For the pesky special characters
					xml;
			//// XSLT processing
			System.out.println(xml);
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			Document xmldoc =null;
			DocumentBuilder builder = factory.newDocumentBuilder();
			xmldoc = builder.parse(new InputSource(new StringReader(xml)));
			log.debug("processXSLTfile2String-"+xslFile);
			Transformer transformer = TransformerFactory.newInstance().newTransformer(new StreamSource(new File(xslFile)));
			StreamResult result = new StreamResult(new StringWriter());
			DOMSource source = new DOMSource(xmldoc);
			for (int i = 0; i < param.length; i++) {
				log.debug("setParemater - "+param[i]+":"+paramVal[i]+"...");
				transformer.setParameter(param[i], paramVal[i]);
				log.debug("ok");
			}
			transformer.transform(source, result);
			log.debug("processXSLTfile2String-"+xslFile);
			return result.getWriter().toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public boolean isCodeExist(String code) {
		return nodeRepository.isCodeExist(code);
	}

	@Override
	@CanRead
	public UUID getPortfolioIdFromNode(@P("id") UUID nodeId) {
		return nodeRepository.findById(nodeId)
				.map(n -> n.getPortfolio().getId())
				.orElse(null);
	}

	@Override
	public String executeMacroOnNode(long userId, UUID nodeId, String macroName) throws JsonProcessingException {

        /// Selection du grid de l'utilisateur
        GroupRights gr = groupRightsRepository.getPublicRightsByUserId(nodeId, userId);
        Long grid = null;
        String label = "";

        if (gr != null) {
            grid = gr.getGroupRightInfo().getId();
            label = gr.getGroupRightInfo().getLabel();
        }

        String tmp = nodeRepository.getMetadataWad(nodeId);

        // FIXME: Check if user has indeed the right to

        MetadataWadDocument document = MetadataWadDocument.from(tmp);

        long resetgroup = getRoleByNode(1L, nodeId, "resetter");

        // Admin or part of "resetter" group
        if ("reset".equals(macroName)
                && (credentialRepository.isAdmin(userId)
                    || securityManager.userHasRole(userId, resetgroup))) {

            resetRights(getChildren(nodeId));

        } else if ("show".equals(macroName) || "hide".equals(macroName)) {

            String roles = document.getShowroles();

            if (roles.contains(label)) {
                String showto = document.getShowtoroles();

                if (!"".equals(showto)) {
                    updateNodeRights(nodeId, Arrays.asList(showto.split(" ")), macroName);
					document.setPrivate("hide".equals(macroName));
                }
            }

            final String metadataAttributes = xmlAttributes(document);

            nodeRepository.findById(nodeId)
				.ifPresent(n -> {
					n.setMetadataWad(metadataAttributes);
					nodeRepository.save(n);
				});
        } else if ("submit".equals(macroName)) {
            List<Node> children = getChildren(nodeId);

            boolean updated = updateNodesRights(children, grid);

            if (!updated)
                return "unchanged";

            /// FIXME: This part might be deprecated in the near future
            String showto = "";

            if (document.getShowtoroles() != null)
                showto = document.getShowtoroles().replace(" ", "','");

            if (!"".equals(showto)) {
                updateNodeRights(nodeId, Collections.singletonList(showto), "show");
                document.setPrivate(false);
            }

            document.setSubmitted(true);
            document.setSubmitteddate(new Date());

            final String metadataAttributes = xmlAttributes(document);

            nodeRepository.findById(nodeId)
					.ifPresent(n -> {
						n.setMetadataWad(metadataAttributes);
						nodeRepository.save(n);
					});

        }

		return "OK";
	}

	@Override
	public void resetRights(List<Node> children) throws JsonProcessingException {
		Map<UUID, Map<String, GroupRights>> resolve = new HashMap<>();

		for (Node child : children) {
			UUID uuid = child.getId();
			String meta = child.getMetadataWad();

			final Map<String, GroupRights> existing = resolve.get(uuid);
			final Map<String, GroupRights> rolesMap = existing != null ? existing : new HashMap<>();

			resolve.put(uuid, rolesMap);

			MetadataWadDocument metadata = MetadataWadDocument.from(meta);

			BiConsumer<String, Consumer<GroupRights>> processRoles = (roleStr, consumer) -> {
				if (roleStr != null) {
					for (String role : roleStr.split(" ")) {
						if (!rolesMap.containsKey(role)) {
							GroupRights gr = new GroupRights();
							consumer.accept(gr);
							rolesMap.put(role, gr);
						}
					}
				}
			};

			processRoles.accept(metadata.getSeenoderoles(),  (gr) -> gr.setRead(true));
			processRoles.accept(metadata.getShowtoroles(),   (gr) -> gr.setRead(false));
			processRoles.accept(metadata.getDelnoderoles(),  (gr) -> gr.setDelete(true));
			processRoles.accept(metadata.getEditnoderoles(), (gr) -> gr.setWrite(true));
			processRoles.accept(metadata.getSubmitroles(),   (gr) -> gr.setSubmit(true));
			processRoles.accept(metadata.getEditresroles(),  (gr) -> gr.setWrite(true));

			if (metadata.getMenuroles() != null) {
				for (String role : metadata.getMenuroles().split(";")) {
					/// Given format:
					/// code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
					String[] tokens = role.split(",");
					String menurolename = tokens[3];

					if (menurolename != null) {
						for (String s : menurolename.split(" "))
							rolesMap.put(s.trim(), new GroupRights());
					}
				}
			}

			if (metadata.getNotifyroles() != null) {
				/// Format pour l'instant: notifyroles="sender responsable"
				String merge = String.join(",", metadata.getNotifyroles().split(" "));

				for (GroupRights value : rolesMap.values()) {
					value.setNotifyRoles(merge);
				}
			}

			/// Now remove mention to being submitted
			Node n = nodeRepository.findById(uuid).get();
			n.setMetadataWad(xmlAttributes(metadata));

			nodeRepository.save(n);

			GroupRightsId grId = new GroupRightsId();

			for (Entry<UUID, Map<String, GroupRights>> entry : resolve.entrySet()) {
				UUID id = entry.getKey();
				Map<String, GroupRights> gr = entry.getValue();

				for (Entry<String, GroupRights> rightElem : gr.entrySet()) {
					String group = rightElem.getKey();

					GroupRights gr2 = groupRightsRepository.getRightsByIdAndLabel(id, group);
					GroupRightInfo gri = gr2 != null ? gr2.getGroupRightInfo() : null;

					GroupRights rightValue = rightElem.getValue();
					grId.setGroupRightInfo(gri);
					grId.setId(id);
					GroupRights toUpdate = groupRightsRepository.findById(grId).get();

					toUpdate.setRead(rightValue.isRead());
					toUpdate.setWrite(rightValue.isWrite());
					toUpdate.setDelete(rightValue.isDelete());
					toUpdate.setSubmit(rightValue.isSubmit());
					toUpdate.setAdd(rightValue.isAdd());
					toUpdate.setTypesId(rightValue.getTypesId());
					toUpdate.setRulesId(rightValue.getRulesId());
					toUpdate.setNotifyRoles(rightValue.getNotifyRoles());

					groupRightsRepository.save(toUpdate);
				}
			}
		}
	}

	@Override
	@IsAdmin
	public long getRoleByNode(Long userId, UUID nodeId, String role) {
		// Check if role exists already
		Long groupId = groupRightInfoRepository.getIdByNodeAndLabel(nodeId, role);

		// If not, create it
		if (groupId == null) {
			Node n = nodeRepository.findById(nodeId).get();

			GroupRightInfo gri = new GroupRightInfo(n.getPortfolio(), role);
			groupRightInfoRepository.save(gri);

			GroupInfo groupInfo = new GroupInfo();

			groupInfo.setGroupRightInfo(new GroupRightInfo(gri.getId()));
			groupInfo.setLabel(role);
			groupInfo.setOwner(1L);

			groupInfoRepository.save(groupInfo);

			groupId = groupInfo.getId();
		}

		return groupId;
	}

	@Override
	@CanWrite
	public String changeNodeMetadataWad(@P("id") UUID nodeId, MetadataWadDocument metadata) throws JsonProcessingException {

		final String metadataAttributes = xmlAttributes(metadata);

		nodeRepository.findById(nodeId)
				.ifPresent(node -> {
					node.setMetadataWad(metadataAttributes);
					nodeRepository.save(node);
					portfolioManager.updateTimeByNode(nodeId);
				});

		return "editer";
	}

	@Override
	@CanDelete
	public void removeNode(@P("id") UUID nodeId) {
		/// Copy portfolio base info
		final Optional<Node> nodeOptional = nodeRepository.findById(nodeId);

		if (!nodeOptional.isPresent())
			return;

		final Node nodeToRemove = nodeOptional.get();

		/// Portfolio id, nécessaire pour plus tard !
		final UUID portfolioId = nodeToRemove.getPortfolio().getId();
		final List<Node> nodes = nodeRepository.getNodesWithResources(portfolioId);

		final Set<Node> nodesToDelete = listHierarchy(nodeToRemove, nodes, null);
		final Set<Resource> resourcesToDelete = new LinkedHashSet<>();

		// On liste les ressources à effacer
		for (Node nodeToDelete : nodesToDelete) {
			if (nodeToDelete.getResource() != null) {
				resourcesToDelete.add(nodeToDelete.getResource());
			}

			if (nodeToDelete.getResResource() != null) {
				resourcesToDelete.add(nodeToDelete.getResResource());
			}

			if (nodeToDelete.getContextResource() != null) {
				resourcesToDelete.add(nodeToDelete.getContextResource());
			}
		}

		portfolioManager.updateTimeByNode(nodeId);
		nodeRepository.deleteAll(nodesToDelete);
		resourceRepository.deleteAll(resourcesToDelete);

		if (nodeToRemove.getParentNode() != null)
			updateNode(nodeToRemove.getParentNode().getId());
	}

	@Override
	@IsAdminOrDesignerOnNode
	public boolean changeParentNode(@P("id") UUID nodeId, UUID parentId) {
		// To avoid defining a node as its parent.
		if (nodeId.equals(parentId))
			return false;

		Optional<Node> nodeOptional = nodeRepository.findById(nodeId);

		if (!nodeOptional.isPresent())
			return false;

		Node node = nodeOptional.get();
		UUID puuid = node.getParentNode() != null ? node.getParentNode().getId() : null;

		Integer next = nodeRepository.getNodeNextOrderChildren(parentId);

		node.setParentNode(new Node(parentId));
		node.setNodeOrder(next);

		nodeRepository.save(node);

		// Mettre à jour la liste d'enfants pour le noeud d'origine et le noeud de
		// destination.
		updateNode(puuid);
		updateNode(parentId);

		portfolioManager.updateTimeByNode(nodeId);

		return true;
	}

	@Override
	public Long moveNodeUp(UUID nodeId) {
		Optional<Node> nodeOptional = nodeRepository.findById(nodeId);

		if (!nodeOptional.isPresent())
			return -1L;

		Node node = nodeOptional.get();

		int order = node.getNodeOrder();
		UUID puuid = node.getParentNode() != null ? node.getParentNode().getId() : null;

		if (order < 0)
			return -1L;
		if (order == 0)
			return -2L;

		final List<Node> nodes = nodeRepository.getNodesByOrder(puuid, order);

		/// Swap node order
		for (Node sibling : nodes) {
			if (sibling.getNodeOrder() == order) {
				sibling.setNodeOrder(order - 1);
			} else {
				sibling.setNodeOrder(order);
			}

			nodeRepository.save(sibling);
		}

		// Mettre à jour la liste des enfants
		updateNode(puuid);
		portfolioManager.updateTimeByNode(nodeId);

		return 0L;
	}

	@Override
	@CanWrite
	public String changeNodeMetadataEpm(@P("id") UUID nodeId, MetadataEpmDocument metadata) throws JsonProcessingException {

		final String metadataAttributes = xmlAttributes(metadata);

		nodeRepository.findById(nodeId)
			.ifPresent(node -> {
				node.setMetadataEpm(metadataAttributes);
				nodeRepository.save(node);

				portfolioManager.updateTimeByNode(nodeId);
			});

		return "editer";
	}

	@Override
	@CanWrite
	public String changeNodeMetadata(@P("id") UUID nodeId, MetadataDocument metadata, Long userId)
			throws BusinessException, JsonProcessingException {

		UUID portfolioUuid = portfolioRepository.getPortfolioUuidFromNode(nodeId);

		if( metadata.getPublic() != null )
			// Public has to be managed via the group/user function
			groupManager.setPublicState(userId, portfolioUuid, metadata.getPublic());

		final String metadataAttributes = xmlAttributes(metadata);

		nodeRepository.findById(nodeId)
			.ifPresent(node -> {
				node.setMetadata(metadataAttributes);
				node.setSemantictag(metadata.getSemantictag());

				if( metadata.getSharedResource() != null )
					node.setSharedRes(metadata.getSharedResource());
				if( metadata.getSharedNode() != null )
					node.setSharedNode(metadata.getSharedNode());
				if( metadata.getSharedNodeResource() != null )
					node.setSharedNodeRes(metadata.getSharedNodeResource());

				nodeRepository.save(node);
				portfolioManager.updateTime(portfolioUuid);
			});

		return "editer";
	}

	@Override
	@CanWrite
	public String changeNodeContext(@P("id") UUID nodeId, ResourceDocument resource, Long userId) throws BusinessException {
		resourceManager.changeResourceByXsiType(nodeId, "context", resource, userId);

		return "editer";
	}

	@Override
	@CanWrite
	public String changeNodeResource(@P("id") UUID nodeId, ResourceDocument resource, Long userId) throws BusinessException {
		resourceManager.changeResourceByXsiType(nodeId, "nodeRes", resource, userId);

		return "editer";
	}

	@Override
	public void changeRights(UUID nodeId, String label, GroupRights rights) {
		GroupRights gr = groupRightsRepository.getRightsByIdAndLabel(nodeId, label);

		if (gr != null) {
			gr.setRead(rights.isRead());
			gr.setWrite(rights.isWrite());
			gr.setDelete(rights.isDelete());
			gr.setSubmit(rights.isSubmit());

			groupRightsRepository.save(gr);
		}
	}

	@Override
	public NodeList getNodes(String rootNodeCode, String childSemtag, Long userId,
			String parentSemtag, String parentNodeCode, Integer cutoff) throws BusinessException {

		UUID pid = portfolioRepository.getPortfolioUuidFromNodeCode(rootNodeCode);
		final NodeList emptyList = new NodeList(Collections.emptyList());

		if (pid == null)
			return emptyList;

		GroupRights rights = portfolioManager.getRightsOnPortfolio(userId, pid);

		if (!rights.isRead()
				&& !credentialRepository.isAdmin(userId)
				&& !portfolioRepository.isPublic(pid)
				&& !portfolioManager.isOwner(userId, pid))
			throw new GenericBusinessException("403 FORBIDDEN : no admin right");

		// Not null, not empty
		// When we have a set, subset, and code of selected item
		/// Searching nodes subset where semtag is under semtag_parent. First filtering
		// is with parentNodeCode
		if (StringUtils.isNotEmpty(parentSemtag) && StringUtils.isNotEmpty(parentNodeCode)) {
			List<Node> nodes = nodeRepository.getNodes(pid);

			/// Init temp set and hashmap
			final Optional<Node> parentTagNode = nodes.stream()
				.filter(n -> StringUtils.equals(n.getCode(), parentNodeCode)
							&& StringUtils.indexOf(n.getSemantictag(), parentSemtag) != -1)
				.findFirst();

			/// Got single parent node, will need nodes under to search
			Stream<Node> nodeStream = listHierarchy(parentTagNode.orElse(null), nodes, cutoff)
					.stream()
					.filter(node -> StringUtils.indexOf(node.getSemantictag(), childSemtag) != -1)
					.sorted((o1, o2) -> {
						int result = StringUtils.compare(o1.getCode(), o2.getCode());

						if (result == 0) {
							return NumberUtils.compare(o1.getNodeOrder(), o2.getNodeOrder());
						}

						return result;
					});

			return new NodeList(nodeStream
					.map(this::getNodeDocument)
					.collect(Collectors.toList()));
		} else {
			if (portfolioRepository.existsById(pid)) {
				List<Node> nodes = nodeRepository.getNodesBySemanticTag(pid, childSemtag);

				return new NodeList(nodes.stream()
						.map(this::getNodeDocument)
						.collect(Collectors.toList()));
			}
		}

		return emptyList;
	}

	private List<Pair<Node, GroupRights>> getNodePerLevel(UUID nodeId, Long userId, Long rrgId, Integer cutoff) {

		Optional<Node> nodeOptional = nodeRepository.findById(nodeId);

		if (!nodeOptional.isPresent())
			return Collections.emptyList();

		Node n = nodeOptional.get();

		UUID portfolioId = n.getPortfolio().getId();
		List<Node> nodes = nodeRepository.getNodes(portfolioId);

		Set<UUID> parentIds = listHierarchy(n, nodes, cutoff)
				.stream()
				.map(Node::getId)
				.collect(Collectors.toSet());

		Map<UUID, GroupRights> rights = new HashMap<>();

		if (credentialRepository.isDesigner(userId, nodeId) || credentialRepository.isAdmin(userId)) {
			for (UUID parentNode : parentIds) {
				GroupRights gr = new GroupRights();
				gr.setId(new GroupRightsId(null, parentNode));
				gr.setRead(true);
				gr.setWrite(true);
				gr.setDelete(true);
				gr.setSubmit(false);
				gr.setAdd(false);

				rights.put(parentNode, gr);
			}
		} else {
			if (nodeRepository.isPublic(nodeId)) {
				for (UUID parentNode : parentIds) {
					GroupRights gr = new GroupRights();
					gr.setId(new GroupRightsId(null, parentNode));
					gr.setRead(true);
					gr.setWrite(false);
					gr.setDelete(true);
					gr.setSubmit(false);
					gr.setAdd(false);

					rights.put(parentNode, gr);
				}
			}

			// Aggregation des droits avec 'all', l'appartenance du groupe de l'utilisateur,
			// et les droits propres a l'utilisateur
			GroupRightInfo gri1 = groupRightInfoRepository.getByPortfolioAndLabel(portfolioId, "all");
			GroupInfo gi = groupUserRepository.getUniqueByUser(userId)
					.getGroupInfo();

			Long grid3 = nodes.stream()
					.filter(node -> node.getId().equals(nodeId) &&
							node.getPortfolio().equals(gi.getGroupRightInfo().getPortfolio()))
					.findFirst()
					.map(node -> gi.getGroupRightInfo().getId())
					.orElse(0L);

			List<GroupRights> grList = groupRightsRepository.getByPortfolioAndGridList(portfolioId, gri1.getId(), rrgId,
					grid3);

			for (UUID ts : parentIds) {
				for (GroupRights item : grList) {
					if (item.getGroupRightsId().equals(ts)) {
						if (rights.containsKey(item.getGroupRightsId())) {
							GroupRights original = rights.get(item.getGroupRightsId());
							original.setRead(item.isRead() || original.isRead());
							original.setWrite(item.isWrite() || original.isWrite());
							original.setDelete(item.isDelete() || original.isDelete());
							original.setSubmit(item.isSubmit() || original.isSubmit());
							original.setAdd(item.isAdd() || original.isAdd());
						} else {
							rights.put(item.getGroupRightsId(), item);
						}
					}
				}
			}
		}

		List<Node> nodeList = nodeRepository.getNodes(new ArrayList<>(parentIds));

		return nodeList.stream()
				.filter(node -> rights.containsKey(node.getId()))
				.filter(node -> rights.get(node.getId()).isRead())
				.map(node -> Pair.of(node, rights.get(node.getId())))
				.collect(Collectors.toList());
	}

    /**
     * Même chose que postImportNode, mais on ne prend pas en compte le parsage des
     * droits
     */
    @CanRead
    public UUID copyNode(@P("id")  UUID destId, String tag, String code, UUID sourceId, Long userId)
            throws BusinessException, JsonProcessingException {
        return importNode(destId, tag, code, sourceId, userId, false);
    }

	private UUID checkCache(UUID portfolioId) {

		boolean setCache = false;
		Node root = portfolioRepository.getPortfolioRootNode(portfolioId);
		Portfolio portfolio = root.getPortfolio();
		String code = null;

		// Le portfolio n'a pas été trouvé, pas besoin d'aller plus loin
		if (portfolio != null) {
			portfolioId = portfolio.getId();
			code = root.getCode();
			// Vérifier si nous n'avons pas déjà le portfolio en cache
			if (cachedNodes.get(portfolioId) != null) {
				final Map<UUID, List<Node>> nodes = cachedNodes.get(portfolioId);
				log.info("Portfolio présent dans le cache pour le code : " + code + " -> " + portfolioId);

				Iterator<List<Node>> iter = nodes.values().iterator();
				// Vérifier si le cache est toujours à jour.
				if (nodes.isEmpty() || portfolio.getModifDate() == null
						|| !portfolio.getModifDate().equals(iter.next().get(1).getModifDate())) {
					// le cache est obsolète
					log.info("Cache obsolète pour : " + code);
					cachedNodes.remove(portfolioId);
					log.info("Supprimé du cache pour : " + code + " -> " + portfolioId);
					setCache = true;
				}
			} else {
				setCache = true;
			}

			if (setCache) // Le portfolio n'est pas/plus présent dans le cache, chargez-le
			{
				log.info("Entrée manquante dans le cache pour le code: " + code);

				// Assignez la date du portfolio pour les noeuds en cache .. Utile pour
				// vérifier la validité du cache.
				final List<Node> nodes = nodeRepository.getNodes(portfolioId);
				/// Since we'll have to loop for a bit with substree,
				/// convert list as hashmap
        HashMap<UUID, List<Node>> resolve = new HashMap<>();
        
				for (Node node : nodes) {
					node.setModifDate(portfolio.getModifDate());
					Node ppNode = node.getParentNode();	// Sometime get proxy object
					Node pNode = (Node) Hibernate.unproxy(ppNode);
					UUID parentId = null;
					/// FIXME: Some other node than root have parent as null (import?)
					if( pNode != null )
						parentId = pNode.getId();
//					else
//						continue;		/// current node is root
					
					List<Node> branch = resolve.get(parentId);
					if( branch == null )
					{
						branch = new ArrayList<Node>();
						branch.add(pNode);	/// Parent node as first eleemnt
						resolve.put(parentId, branch);
					}
					node = (Node) Hibernate.unproxy(node);
					Resource r = node.getResource();
					if( r != null ) node.setResource((Resource) Hibernate.unproxy(r));
					r = node.getContextResource();
					if( r != null ) node.setContextResource((Resource) Hibernate.unproxy(r));
					r = node.getResResource();
					if( r != null ) node.setResResource((Resource) Hibernate.unproxy(r));
					branch.add(node);
				}
				// Mettre tous les noeuds dans le cache.
				cachedNodes.put(portfolioId, resolve);
			}
		}

		return portfolioId;
	}

	@Override
	@CanRead
	public UUID importNode(@P("id") UUID destId, String tag, String code, UUID sourceId, Long userId)
            throws BusinessException, JsonProcessingException {
	    return importNode(destId, tag, code, sourceId, userId, true);
    }

    private UUID importNode(UUID destId, String tag, String code, UUID sourceId, Long userId, boolean parseRights)
            throws BusinessException, JsonProcessingException {
		if ((StringUtils.isEmpty(tag) || StringUtils.isEmpty(code)) && sourceId == null) {
            throw new IllegalArgumentException(
                    "importNode() a reçu des paramètres non valides (complétez le paramètre 'srcuuid' ou les paramètres 'tag' et 'code').");
		}
        
        // Pour la copie de la structure
        UUID portfolioId = null;

        Node baseNode = null;
        Map<UUID, List<Node>> nodes = null;

        // On évite la recherche de balises puisque nous connaissons l'uuid du noeud à
        // copier.
        if (sourceId != null) {
            // Puisque nous ne savons pas si ces noeuds doivent être mis en cache, on
            // recherche les informations dans la base.
            UUID portfolioUuid = portfolioRepository.getPortfolioUuidFromNode(sourceId);
            if( portfolioUuid == null )
          		throw new GenericBusinessException("Aucun noeud trouvé pour le UUID : " + sourceId);

            /// Check cache validity
            checkCache( portfolioUuid );
            
            // Keep copy for later
            nodes = cachedNodes.get(portfolioUuid);

            /// Fetch base node from child list;
            List<Node> clist = nodes.get(sourceId);
            baseNode = clist.get(0);	// First element is parent
        } else {
        	/// Code/Tag
        	Portfolio portfolio = portfolioRepository.getPortfolioFromNodeCode(code);
        	if ( portfolio == null) {
        		throw new GenericBusinessException("Aucun noeud trouvé pour le code : " + code);
        	}
        	portfolioId = checkCache(portfolio.getId());
            // Get nodes from portfolio we need to copy from cache
            nodes = cachedNodes.get(portfolioId);

            // Check whether we can find a node with the given tag
            Node nodeSearch = null;

            /// For all branches
            for (List<Node> lnode : nodes.values()) {
            	Iterator<Node> inode = lnode.iterator();
            	/// Skip first in list, it's the parent
            	inode.next();
            	// Directly check if it's either in code or semantictag
            	while( inode.hasNext() )
            	{
            		Node n = inode.next();
            		if (StringUtils.equalsIgnoreCase(n.getCode(), tag) || StringUtils.equalsIgnoreCase(n.getSemantictag(), tag)) {
            			nodeSearch = n;
            			break;
            		}
            		
            	}
            }

            if (nodeSearch != null) {
            	baseNode = nodeSearch;
            } else {
                throw new GenericBusinessException(
                        "Aucun noeud trouvé pour le code : " + code + " et le tag : " + tag);
            }
        }
        
        final Node destNode = nodeRepository.findById(destId).get();
        int nodeOrder = nodeRepository.getFirstLevelChildren(destId).size();
        Portfolio destPortfolio = destNode.getPortfolio();
        
        /// baseuuid is the starting node to copy from

        Node baseCopyNode = new Node(baseNode);
        baseCopyNode.setParentNode(destNode);
        baseCopyNode.setModifUserId(userId);
        baseCopyNode.setPortfolio(destPortfolio);
        baseCopyNode.setNodeOrder(nodeOrder+1);
        Resource r = baseCopyNode.getContextResource();
        if( r != null ) resourceRepository.save(r);
        r = baseCopyNode.getResource();
        if( r != null ) resourceRepository.save(r);
        r = baseCopyNode.getResResource();
        if( r != null ) resourceRepository.save(r);
        baseCopyNode = nodeRepository.save(baseCopyNode);
        
        /// Ajout de l'enfant dans le noeud de destination
        String destClist = destNode.getChildrenStr();
        destClist += ","+baseCopyNode.getId().toString();
        destNode.setChildrenStr(destClist);
        nodeRepository.save(destNode);

        /// Contient les noeuds à copier.
        final Set<Node> nodesToCopy = new LinkedHashSet<>();

        /// Contient les uuid des noeuds à copier.
        Queue<Node> resolveParent = new LinkedList<>();

        /// Noeud de reference a traverser
        Queue<Node> refParent = new LinkedList<>();
        
        nodesToCopy.add(baseCopyNode);
        resolveParent.add(baseCopyNode);
        refParent.add(baseNode);

        Node refNode = baseNode;
        Node qnode = baseCopyNode;

        while ( !refParent.isEmpty() ) {
        	// Current parent to traverse
        	refNode = refParent.poll();
        	// Current parent to attach to
        	qnode = resolveParent.poll();
            //// Retrieve childrens
          	List<Node> childs = nodes.get(refNode.getId());
          	if( childs == null )	// Leaf
        		{
          		continue;
        		}
          	/// First element in list is the parent node, remove it
          	childs.remove(0);
          	List<String> cList = new ArrayList<>();
          	/// For listed childs
              for (Node cNode : childs ) {
              	/// Copy and adjust values
              	Node ccopy = new Node(cNode);
              	ccopy.setParentNode(qnode);
              	ccopy.setModifUserId(userId);
              	ccopy.setPortfolio(destPortfolio);
              	
                Resource r2 = ccopy.getContextResource();
                if( r2 != null ) resourceRepository.save(r2);
                r2 = ccopy.getResource();
                if( r2 != null ) resourceRepository.save(r2);
                r2 = ccopy.getResResource();
                if( r2 != null ) resourceRepository.save(r2);

              	ccopy = nodeRepository.save(ccopy);
              	cList.add(ccopy.getId().toString());
              	
              	/// Copy list to submit (Or need to submit before)
              	nodesToCopy.add(ccopy);
              	/// Add new parent to attach new child to
              	resolveParent.add(ccopy);
              	/// Need to traverse this node next
              	refParent.add(cNode);
              }
              qnode.setChildrenStr(String.join(",", cList));
              nodeRepository.save(qnode);
              
        }

        //////////////////////////////////////////
        /// Copie des noeuds et des ressources ///
        /////////////////////////////////////////

        // Contain a mapping between original elements and their copy.
        final Map<Node, Node> allNodes = new HashMap<>();

        for (Node node : nodesToCopy) {

        	/*
        	Arrays.asList(node.getResource(), node.getResResource(), node.getContextResource()).forEach(original -> {
        		if (original != null) {
        			Resource resourceCopy = new Resource(original);
        			resourceCopy.setModifUserId(userId);

					if (!node.isSharedRes() || !node.getSharedNode() || !node.isSharedNodeRes()) {
						resourceCopy.setNode(node);
						resourceRepository.save(resourceCopy);

						if (original.getId() == node.getResource().getId()) {
							node.setResource(resourceCopy);
						} else if (original.getId() == node.getResResource().getId()) {
							node.setResource(resourceCopy);
						} else {
							node.setContextResource(resourceCopy);
						}

						nodeRepository.save(node);
					}
				}
			});
					//*/

            allNodes.put(node, node);
        }


        //////////////////////////////////
        /// Copie des droits des noeuds ///
        /////////////////////////////////
        // Login
        final String login = credentialRepository.getLoginById(userId);

        /// Copier les rôles actuel @Override pour faciliter le référencement.
        final UUID tmpPortfolioUuid = portfolioRepository.getPortfolioUuidFromNode(destId);

        // Récupération des rôles dans la BDD.
        final List<GroupRightInfo> griList = groupRightInfoRepository.getByPortfolioID(tmpPortfolioUuid);

        //// Set temporaire roles
        final Set<GroupRightInfo> t_set_groupRightInfo = new HashSet<>(griList);
        final Map<GroupRightsId, GroupRights> t_group_rights = new HashMap<>();

        GroupRights t_gr = null;
        final boolean hasGroup = !griList.isEmpty();

        /// Gestion des droits
        if (parseRights && hasGroup) {
            String onlyuser = "(?<![-=+])(user)(?![-=+])";
            Pattern pattern = Pattern.compile(onlyuser);

            for (Entry<Node, Node> entry : allNodes.entrySet()) {
                boolean found = false;
                GroupRightsId grId = new GroupRightsId();

                Node original = entry.getKey();
                Node copy = entry.getValue();

                UUID uuid = copy.getId();
                UUID portfolioUuid = copy.getPortfolio().getId();

                // Process et remplacement de 'user' par la personne en cours
                String meta = original.getMetadataWad();

                Matcher matcher = pattern.matcher(meta);

                if (matcher.find()) {
                    meta = meta.replaceAll(onlyuser, login);

                    // Remplacer les métadonnées par le nom d'utilisateur actuel
                    copy.setMetadataWad(meta);
                    nodeRepository.save(copy);

                    // S'assurer qu'un groupe d'utilisateurs spécifique existe en base et y ajouter
                    // l'utilisateur.
                    long ngid = getRoleByNode(1L, destId, login);
                    securityManager.addUserToGroup(userId, ngid);

                    /// Ensure entry is there in temp table, just need a skeleton info
                    GroupRightInfo groupRightInfo = new GroupRightInfo();

                    groupRightInfo.setId(ngid);
                    groupRightInfo.setLabel(login);
                    groupRightInfo.setOwner(1L);
                    t_set_groupRightInfo.add(groupRightInfo);
                }

                MetadataWadDocument metadata = MetadataWadDocument.from(meta);

                // FIXME: à améliorer pour faciliter le changement des droits
                String nodeRole;

                if (metadata.getSeenoderoles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getSeenoderoles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo tmp_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(tmp_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(tmp_gri);
                                grId.setId(uuid);

                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setRead(true);
                                } else {
                                    GroupRights gr = new GroupRights();
                                    gr.setId(grId);
                                    gr.setRead(true);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (seenoderoles)");
                        }
                    }
                }

                if (metadata.getShowtoroles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getShowtoroles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo tmp_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(tmp_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(tmp_gri);
                                grId.setId(uuid);
                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setRead(false);
                                } else {
                                    t_gr = new GroupRights();
                                    t_gr.setId(grId);
                                    t_gr.setRead(false);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (showtoroles)");
                        }
                    }
                }

                if (metadata.getDelnoderoles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getDelnoderoles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo t_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(t_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(t_gri);
                                grId.setId(uuid);

                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setDelete(true);
                                } else {
                                    t_gr = new GroupRights();
                                    t_gr.setId(grId);
                                    t_gr.setDelete(true);
                                    t_gr.setRead(false);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (delroles)");
                        }
                    }
                }

                if (metadata.getEditnoderoles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getEditnoderoles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo tmp_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(tmp_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(tmp_gri);
                                grId.setId(uuid);

                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setWrite(true);
                                } else {
                                    t_gr = new GroupRights();
                                    t_gr.setId(grId);
                                    t_gr.setWrite(true);
                                    t_gr.setRead(false);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (editnoderoles)");
                        }
                    }
                }

                if (metadata.getSubmitroles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getSubmitroles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo tmp_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(tmp_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(tmp_gri);
                                grId.setId(uuid);

                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setSubmit(true);
                                } else {
                                    t_gr = new GroupRights();
                                    t_gr.setId(grId);
                                    t_gr.setSubmit(true);
                                    t_gr.setRead(false);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (submitroles)");
                        }
                    }
                }

                if (metadata.getEditresroles() != null) {
                    StringTokenizer tokens = new StringTokenizer(metadata.getEditresroles(), " ");

                    while (tokens.hasMoreElements()) {
                        nodeRole = tokens.nextElement().toString();
                        for (GroupRightInfo tmp_gri : t_set_groupRightInfo) {
                            if (StringUtils.equalsIgnoreCase(tmp_gri.getLabel(), nodeRole)) {
                                grId.setGroupRightInfo(tmp_gri);
                                grId.setId(uuid);

                                if (t_group_rights.containsKey(grId)) {
                                    t_gr = t_group_rights.get(grId);
                                    t_gr.setWrite(true);
                                } else {
                                    t_gr = new GroupRights();
                                    t_gr.setId(grId);
                                    t_gr.setWrite(true);
                                    t_gr.setRead(false);
                                    t_group_rights.put(grId, t_gr);
                                }
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            log.error("Role '" + nodeRole
                                    + "' might not exist in destination portfolio. (editresroles)");
                        }
                    }
                }


                /// FIXME: Incomplete

                if (metadata.getMenuroles() != null) {
                    /// Pour les différents items du menu
                    StringTokenizer menuline = new StringTokenizer(metadata.getMenuroles(), ";");

                    while (menuline.hasMoreTokens()) {
                        String line = menuline.nextToken();
                        /// Format pour l'instant:
                        /// code_portfolio,tag_semantique,label@en/libelle@fr,reles[;autre menu]
                        String[] tokens = line.split(",");
                        String menurolename = null;
                        for (int t = 0; t < 4; ++t)
                            menurolename = tokens[3];

                        if (menurolename != null) {
                            // Break down list of roles
                            String[] roles = menurolename.split(" ");
                            for (String role : roles) {
                                // Ensure roles exists
                                securityManager.addRole(portfolioId, role, 1L);
                            }
                        }
                    }
                }

                if (metadata.getNotifyroles() != null) {
                    groupManager.changeNotifyRoles(portfolioUuid, uuid,
							metadata.getNotifyroles().replace(" ", ","));
                }
            }

            /// Ajout des droits des noeuds
            for (GroupRights gr : t_group_rights.values()) {
            	if( gr != null )
                groupRightsRepository.save(gr);
            }
        }

		return baseCopyNode.getId();

	}

	@Override
	public List<Node> getChildren(UUID nodeId) {
		final Map<Integer, List<Node>> tree = new HashMap<>();
		final Optional<Node> root = nodeRepository.findById(nodeId);

		if (root.isPresent()) {
			return getChildren(Collections.singletonList(root.get()), tree, 0);
		} else {
			return Collections.emptyList();
		}
	}

	private List<Node> getChildren(List<Node> nodes, Map<Integer, List<Node>> tree, int level) {
		List<Node> children = childrenFor(nodes);

		if (!children.isEmpty()) {
			tree.put(level, children);

			return getChildren(children, tree, level + 1);
		} else {
			return tree
					.values()
					.stream()
					.flatMap(List::stream)
					.collect(Collectors.toList());
		}
	}

	private List<Node> childrenFor(List<Node> nodes) {
		List<UUID> ids = nodes.stream().map(Node::getId).collect(Collectors.toList());
		return nodeRepository.getDirectChildren(ids);
	}

	public void updateNodeRights(UUID nodeUuid, List<String> labels, String macroName) {
		if (!labels.isEmpty()) {
			for (int i = 0; i < labels.size(); i++) {
				labels.set(i, StringUtils.prependIfMissing(labels.get(i), "'", "\""));
				labels.set(i, StringUtils.appendIfMissing(labels.get(i), "'", "\""));
			}
		}

		List<GroupRights> grList = groupRightsRepository.findByIdAndLabels(nodeUuid, labels);

		grList.forEach(gr -> {
			if ("hide".equals(macroName)) {
				gr.setRead(false);
			} else if ("show".equals(macroName)) {
				gr.setRead(true);
			}
		});

		groupRightsRepository.saveAll(grList);
	}

	public boolean updateNodesRights(List<Node> nodes, Long grid) {
		for (Node node : nodes) {
			GroupRights gr = groupRightsRepository.getRightsByGrid(node.getId(), grid);

			if (gr != null) {
				gr.setWrite(false);
				gr.setDelete(false);
				gr.setAdd(false);
				gr.setSubmit(false);
				gr.setTypesId(null);
				gr.setRulesId(null);
			} else {
				gr = new GroupRights();
				gr.setId(new GroupRightsId(new GroupRightInfo(grid), node.getId()));
			}

			groupRightsRepository.save(gr);
		}

		return !nodes.isEmpty();
	}

	private void updateNode(UUID nodeId) {
		List<Node> nodes = nodeRepository.getFirstLevelChildren(nodeId);

		/// Re-numérote les noeuds (on commence à 0)
		for (int i = 0; i < nodes.size(); i++) {
			nodes.get(i).setNodeOrder(i);
		}

		nodeRepository.saveAll(nodes);

		String uuidsStr = nodeRepository.getParentNodeUUIDs(nodeId)
								.stream()
								.map(UUID::toString)
								.collect(Collectors.joining(","));

		nodeRepository.findById(nodeId)
				.ifPresent(node -> {
					node.setChildrenStr(uuidsStr);
					nodeRepository.save(node);
				});

	}

	private Node add(NodeDocument nodeDocument, UUID nodeId, Node nodeParent, String semanticTag, String code, int order, Long userId,
					 UUID portfolioId) throws JsonProcessingException {
		
		Node node = new Node();
		node.setId(nodeId);

		node.setParentNode(nodeParent);
		node.setChildrenStr("");

		node.setNodeOrder(order);
		node.setAsmType(nodeDocument.getType());
		node.setXsiType(nodeDocument.getXsiType());

		MetadataDocument metadata = nodeDocument.getMetadata();

		if (metadata != null) {
			node.setSharedRes(metadata.getSharedResource());
			node.setSharedNode(metadata.getSharedNode());
			node.setSharedNodeRes(metadata.getSharedNodeResource());
		}

		if (nodeDocument.getMetadata() != null)
		{
			String attrvalue = xmlAttributes(nodeDocument.getMetadata());
			node.setMetadata(attrvalue);
		}

		if (nodeDocument.getMetadataWad() != null)
		{
			String attrvalue = xmlAttributes(nodeDocument.getMetadataWad());
			node.setMetadataWad(attrvalue);
		}

		if (nodeDocument.getMetadataEpm() != null)
		{
			String attrvalue = xmlAttributes(nodeDocument.getMetadataEpm());
			node.setMetadataEpm(attrvalue);
		}

		node.setSemtag(nodeDocument.getSemtag());
		node.setSemantictag(semanticTag);
		node.setLabel(nodeDocument.getLabel());
		node.setCode(code);
		node.setDescr(nodeDocument.getDescription());
		node.setFormat(nodeDocument.getFormat());
		node.setModifUserId(userId);

		if (portfolioId != null) {
			portfolioRepository.findById(portfolioId)
				.ifPresent(node::setPortfolio);
		}

		return nodeRepository.save(node);
	}

	private NodeDocument getNodeDocument(Node node) {
		NodeDocument nodeDocument = new NodeDocument(node);

		List<ResourceDocument> resources = new ArrayList<>();

		if (node.getMetadata() != null) {
			try {
				nodeDocument.setMetadata(MetadataDocument.from(node.getMetadata()));
			} catch (JsonProcessingException ignored)  { }
		}

		if (node.getMetadataEpm() != null) {
			try {
				nodeDocument.setMetadataEpm(MetadataEpmDocument.from(node.getMetadataEpm()));
			} catch (JsonProcessingException ignored)  { }
		}

		if (node.getMetadataWad() != null) {
			try {
				nodeDocument.setMetadataWad(MetadataWadDocument.from(node.getMetadataWad()));
			} catch (JsonProcessingException ignored) { }
		}

		if (node.getResource() != null) {
			resources.add(new ResourceDocument(node.getResource(), node));
		}

		if (node.getResResource() != null) {
			resources.add(new ResourceDocument(node.getResResource(), node));
		}

		if (node.getContextResource() != null) {
			resources.add(new ResourceDocument(node.getContextResource(), node));
		}

		nodeDocument.setResources(resources);

		return nodeDocument;
	}

	@Override
	public NodeRightsDocument getRights(UUID nodeId, Long userId) {
		GroupRights groupRights = getRights(userId, nodeId);

		return new NodeRightsDocument(nodeId, groupRights);
	}

	private Set<Node> listHierarchy(Node base, List<Node> nodes, Integer cutoff) {
		Map<Integer, Set<Node>> treeMap = new HashMap<>();
		Set<Node> allChildren = new LinkedHashSet<>();

		if (base != null)
			allChildren.add(base);

		treeMap.put(0, allChildren);

		int level = 0;
		boolean added = true;

		while (added && (cutoff == null || level < cutoff)) {
			Set<Node> parentNodes = treeMap.get(level);

			Set<Node> children = nodes.stream()
					.filter(node -> node.getParentNode() != null)
					.filter(node -> parentNodes.stream().anyMatch(n -> n.getId().equals(node.getParentNode().getId())))
					.collect(Collectors.toSet());

			treeMap.put(level + 1, children);
			allChildren.addAll(children);

			added = !children.isEmpty();
			level++;
		}

		return allChildren;
	}
}
