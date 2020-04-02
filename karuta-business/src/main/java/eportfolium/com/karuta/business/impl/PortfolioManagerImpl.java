package eportfolium.com.karuta.business.impl;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.activation.MimeTypeParseException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eportfolium.com.karuta.business.contract.FileManager;
import eportfolium.com.karuta.business.contract.GroupManager;
import eportfolium.com.karuta.business.contract.NodeManager;
import eportfolium.com.karuta.business.contract.PortfolioManager;
import eportfolium.com.karuta.business.contract.SecurityManager;
import eportfolium.com.karuta.consumer.contract.dao.ConfigurationDao;
import eportfolium.com.karuta.consumer.contract.dao.CredentialDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupInfoDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupRightInfoDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupRightsDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupUserDao;
import eportfolium.com.karuta.consumer.contract.dao.NodeDao;
import eportfolium.com.karuta.consumer.contract.dao.PortfolioDao;
import eportfolium.com.karuta.consumer.contract.dao.PortfolioGroupDao;
import eportfolium.com.karuta.consumer.contract.dao.PortfolioGroupMembersDao;
import eportfolium.com.karuta.consumer.contract.dao.ResourceTableDao;
import eportfolium.com.karuta.consumer.util.DomUtils;
import eportfolium.com.karuta.model.bean.Credential;
import eportfolium.com.karuta.model.bean.GroupInfo;
import eportfolium.com.karuta.model.bean.GroupRightInfo;
import eportfolium.com.karuta.model.bean.GroupRights;
import eportfolium.com.karuta.model.bean.GroupRightsId;
import eportfolium.com.karuta.model.bean.GroupUser;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.bean.Portfolio;
import eportfolium.com.karuta.model.bean.PortfolioGroup;
import eportfolium.com.karuta.model.bean.PortfolioGroupMembers;
import eportfolium.com.karuta.model.bean.PortfolioGroupMembersId;
import eportfolium.com.karuta.model.bean.ResourceTable;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.model.exception.DoesNotExistException;
import eportfolium.com.karuta.model.exception.GenericBusinessException;
import eportfolium.com.karuta.util.JavaTimeUtil;
import eportfolium.com.karuta.util.PhpUtil;
import eportfolium.com.karuta.util.Tools;
import eportfolium.com.karuta.util.ValidateUtil;

@Service
@Transactional
public class PortfolioManagerImpl extends BaseManager implements PortfolioManager {

	@Autowired
	private NodeManager nodeManager;

	@Autowired
	private SecurityManager securityManager;

	@Autowired
	private GroupManager groupManager;

	@Autowired
	private PortfolioDao portfolioDao;

	@Autowired
	private PortfolioGroupDao portfolioGroupDao;

	@Autowired
	private PortfolioGroupMembersDao portfolioGroupMembersDao;

	@Autowired
	private NodeDao nodeDao;

	@Autowired
	private CredentialDao credentialDao;

	@Autowired
	private GroupRightsDao groupRightsDao;

	@Autowired
	private GroupRightInfoDao groupRightInfoDao;

	@Autowired
	private GroupInfoDao groupInfoDao;

	@Autowired
	private ResourceTableDao resourceTableDao;

	@Autowired
	private GroupUserDao groupUserDao;

	@Autowired
	private ConfigurationDao configurationDao;

	@Autowired
	private FileManager fileManager;

	/// temp class

	class groupright {
		right getGroup(String label) {
			right r = rights.get(label.trim());
			if (r == null) {
				r = new right();
				rights.put(label, r);
			}
			return r;
		}

		void setNotify(String roles) {
			Iterator<right> iter = rights.values().iterator();
			while (iter.hasNext()) {
				right r = iter.next();
				r.notify = roles.trim();
			}
		}

		Map<String, right> rights = new HashMap<String, right>();
	}

	class resolver {
		groupright getUuid(String uuid) {
			groupright gr = resolve.get(uuid);
			if (gr == null) {
				gr = new groupright();
				resolve.put(uuid, gr);
			}
			return gr;
		};

		Map<String, groupright> resolve = new HashMap<String, groupright>();
		Map<String, Long> groups = new HashMap<String, Long>();
	}

	static private final Logger logger = LoggerFactory.getLogger(PortfolioManagerImpl.class);

	public boolean changePortfolioDate(String fromNodeuuid, String fromPortuuid) {
		boolean hasChanged = false;
		final Date now = JavaTimeUtil.toJavaDate(LocalDateTime.now(JavaTimeUtil.date_default_timezone));

		try {
			Portfolio portfolio = null;
			if (fromNodeuuid != null) {
				UUID portfolioUUID = nodeDao.getPortfolioIdFromNode(fromNodeuuid);
				portfolio = portfolioDao.findById(portfolioUUID);
				portfolio.setModifDate(now);
				hasChanged = true;
			} else if (fromPortuuid != null) {
				portfolio = portfolioDao.findById(fromPortuuid);
				portfolio.setModifDate(now);
				hasChanged = true;
			}
			portfolioDao.merge(portfolio);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return hasChanged;
	}

	public boolean removePortfolioGroups(Long portfolioGroupId) {
		boolean res = false;
		try {
			PortfolioGroup pg = portfolioGroupDao.findById(portfolioGroupId);
			List<PortfolioGroupMembers> results = portfolioGroupMembersDao.getByPortfolioGroupID(portfolioGroupId);
			Iterator<PortfolioGroupMembers> it = results.iterator();
			while (it.hasNext()) {
				portfolioGroupMembersDao.remove(it.next());
			}
			portfolioGroupDao.remove(pg);
			res = true;

		} catch (Exception e) {
			e.printStackTrace();
		}

		return res;
	}

	public boolean removePortfolioFromPortfolioGroups(String portfolioUuid, Long portfolioGroupId) {
		boolean result = false;

		try {
			PortfolioGroupMembersId pgmID = new PortfolioGroupMembersId();
			pgmID.setPortfolio(new Portfolio(UUID.fromString(portfolioUuid)));
			pgmID.setPortfolioGroup(new PortfolioGroup(portfolioGroupId));
			portfolioGroupMembersDao.removeById(pgmID);
			result = true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public String getPortfoliosByPortfolioGroup(Long portfolioGroupId) {
		StringBuilder result = new StringBuilder();
		result.append("<group id=\"").append(portfolioGroupId).append("\">");
		List<Portfolio> portfolios = portfolioGroupDao.getPortfoliosByPortfolioGroup(portfolioGroupId);
		Iterator<Portfolio> it = portfolios.iterator();
		while (it.hasNext()) {
			result.append("<portfolio");
			result.append(" id=\"");
			result.append(it.next().getId().toString());
			result.append("\"");
			result.append(">");
			result.append("</portfolio>");
		}
		result.append("</group>");
		return result.toString();
	}

	public int changePortfolioActive(String portfolioUuid, Boolean active) {
		int result = -1;
		try {
			Portfolio portfolio = portfolioDao.findById(UUID.fromString(portfolioUuid));
			portfolio.setActive(BooleanUtils.toInteger(active));
			portfolioDao.merge(portfolio);
			result = 0;
		} catch (DoesNotExistException e) {
			e.printStackTrace();
		}
		return result;
	}

	public String getPortfolio(MimeType outMimeType, String portfolioUuid, Long userId, Long groupId, String label,
			String resource, String files, long substid, Integer cutoff)
			throws DoesNotExistException, BusinessException, Exception {

		Node rootNode = portfolioDao.getPortfolioRootNode(portfolioUuid);
		String header = "";
		String footer = "";

		GroupRights rights = getRightsOnPortfolio(userId, groupId, portfolioUuid);
		if (!rights.isRead()) {
			userId = credentialDao.getPublicUid();
			/// Vérifie les droits avec le compte publique (dernière chance)
			GroupRights publicRights = groupRightsDao.getPublicRightsByUserId(rootNode.getId(), userId);
			if (!publicRights.isRead()) {
				throw new GenericBusinessException("Vous n'avez pas les droits nécessaires.");
			}
		}

		if (outMimeType.getSubtype().equals("xml")) {
			Long ownerId = portfolioDao.getOwner(portfolioUuid);
			boolean isOwner = false;
			if (ownerId == userId)
				isOwner = true;

			String headerXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolio code=\"0\" id=\"" + portfolioUuid
					+ "\" owner=\"" + isOwner + "\"><version>4</version>";

			String data = getLinearXml(portfolioUuid, rootNode.getId().toString(), null, true, null, userId,
					rights.getGroupRightInfo().getId(), rights.getGroupRightInfo().getLabel(), cutoff);

			StringWriter stw = new StringWriter();
			stw.append(headerXML + data + "</portfolio>");

			if (resource != null && files != null) {
				if (resource.equals("true") && files.equals("true")) {
					String adressedufichier = System.getProperty("user.dir") + "/tmp_getPortfolio_" + new Date()
							+ ".xml";
					String adresseduzip = System.getProperty("user.dir") + "/tmp_getPortfolio_" + new Date() + ".zip";

					File file = null;
					PrintWriter ecrire;
					try {
						file = new File(adressedufichier);
						ecrire = new PrintWriter(new FileOutputStream(adressedufichier));
						ecrire.println(stw.toString());
						ecrire.flush();
						ecrire.close();
						System.out.print("fichier cree ");
					} catch (IOException ioe) {
						System.out.print("Erreur : ");
						ioe.printStackTrace();
					}

					try {
						ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(adresseduzip));
						zip.setMethod(ZipOutputStream.DEFLATED);
						zip.setLevel(Deflater.BEST_COMPRESSION);
						File dataDirectories = new File(file.getName());
						FileInputStream fis = new FileInputStream(dataDirectories);
						byte[] bytes = new byte[fis.available()];
						fis.read(bytes);

						ZipEntry entry = new ZipEntry(file.getName());
						entry.setTime(dataDirectories.lastModified());
						zip.putNextEntry(entry);
						zip.write(bytes);
						zip.closeEntry();
						fis.close();
						zip.close();
						file.delete();

						return adresseduzip;
					} catch (FileNotFoundException fileNotFound) {
						fileNotFound.printStackTrace();
					} catch (IOException io) {
						io.printStackTrace();
					}
				}
			}

			return stw.toString();
		} else if (outMimeType.getSubtype().equals("json")) {
			header = "{\"portfolio\": { \"-xmlns:xsi\": \"http://www.w3.org/2001/XMLSchema-instance\",\"-schemaVersion\": \"1.0\",";
			footer = "}}";
		}

		return header + nodeManager
				.getNode(outMimeType, rootNode.getId().toString(), true, userId, groupId, label, cutoff).toString()
				+ footer;
	}

	private String getLinearXml(String portfolioUuid, String rootuuid, Node portfolio, boolean withChildren,
			String withChildrenOfXsiType, Long userId, Long groupId, String role, Integer cutoff)
			throws SQLException, SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
		DocumentBuilder parse = newInstance.newDocumentBuilder();

		long time0 = 0;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		long time4 = 0;
		long time5 = 0;

		time0 = System.currentTimeMillis();

		List<Pair<Node, GroupRights>> portfolioStructure = getPortfolioStructure(portfolioUuid, userId, groupId,
				cutoff);

		time1 = System.currentTimeMillis();

		Map<String, Object[]> resolve = new HashMap<String, Object[]>();
		/// Node -> parent
		Map<String, t_tree> entries = new HashMap<String, t_tree>();

		processQuery(portfolioStructure, resolve, entries, null, parse, role);

		time2 = System.currentTimeMillis();

		portfolioStructure = getSharedStructure(portfolioUuid, userId, groupId, cutoff);

		time3 = System.currentTimeMillis();

		if (portfolioStructure != null) {
			processQuery(portfolioStructure, resolve, entries, null, parse, role);
		}

		time4 = System.currentTimeMillis();

		/// Reconstruct functional tree
		t_tree root = entries.get(rootuuid);
		StringBuilder out = new StringBuilder(256);
		if (root != null)
			reconstructTree(out, root, entries);

		time5 = System.currentTimeMillis();

		System.out.println("---- Portfolio ---");
		System.out.println("Query Main: " + (time1 - time0));
		System.out.println("Parsing Main: " + (time2 - time1));
		System.out.println("Query shared: " + (time3 - time2));
		System.out.println("Parsing shared: " + (time4 - time3));
		System.out.println("Reconstruction a: " + (time5 - time4));
		System.out.println("------------------"); //

		return out.toString();
	}

	private List<Pair<Node, GroupRights>> getPortfolioStructure(String portfolioUuid, Long userId, Long groupId,
			Integer cutoff) {

		long time0 = 0;
		long time1 = 0;
		long time2 = 0;
		long time3 = 0;
		long time4 = 0;
		long time5 = 0;
		long time6 = 0;

		List<Pair<Node, GroupRights>> portfolioStructure = new ArrayList<Pair<Node, GroupRights>>();

		time0 = System.currentTimeMillis();

		Node rootNode = portfolioDao.getPortfolioRootNode(portfolioUuid);
		time1 = System.currentTimeMillis();
		GroupRights rights = null;

		// Cas admin, designer, owner
		if (rootNode != null
				&& (credentialDao.isAdmin(userId) || credentialDao.isDesigner(userId, rootNode.getId().toString())
						|| userId == portfolioDao.getOwner(portfolioUuid))) {
			List<Node> nodes = nodeDao.getNodesWithResources(portfolioUuid);
			for (Node node : nodes) {
				rights = new GroupRights(new GroupRightsId(new GroupRightInfo(), null), true, true, true, true, true);
				portfolioStructure.add(Pair.of(node, rights));
			}
		}
		/// FIXME: Il faudrait peut-être prendre une autre stratégie pour sélectionner
		/// les bonnes données : Cas propriétaire OU cas general (via les droits
		/// partagés)
		else if (hasRights(userId, portfolioUuid)) {
			time2 = System.currentTimeMillis();
			Map<String, GroupRights> t_rights_22 = new HashMap<String, GroupRights>();
			time3 = System.currentTimeMillis();

			String login = credentialDao.getLoginById(userId);
//				FIXME: Devrait peut-être verifier si la personne a les droits d'y accéder?
			List<GroupRights> grList = groupRightsDao.getPortfolioAndUserRights(UUID.fromString(portfolioUuid), login,
					groupId);
			for (GroupRights gr : grList) {
				if (t_rights_22.containsKey(gr.getGroupRightsId().toString())) {
					GroupRights original = t_rights_22.get(gr.getGroupRightsId().toString());
					original.setRead(Boolean.logicalOr(gr.isRead(), original.isRead()));
					original.setWrite(Boolean.logicalOr(gr.isWrite(), original.isWrite()));
					original.setDelete(Boolean.logicalOr(gr.isDelete(), original.isDelete()));
					original.setSubmit(Boolean.logicalOr(gr.isSubmit(), original.isSubmit()));
					original.setAdd(Boolean.logicalOr(gr.isAdd(), original.isAdd()));
				} else {
					t_rights_22.put(gr.getGroupRightsId().toString(), gr);
				}
			}

			time4 = System.currentTimeMillis();

			List<Node> nodes = nodeDao.getNodes(new ArrayList<>(t_rights_22.keySet()));

			// Sélectionne les données selon la filtration
			for (Node node : nodes) {
				if (t_rights_22.containsKey(node.getId().toString())) { // Verification des droits
					rights = t_rights_22.get(node.getId().toString());
					if (rights.isRead()) { // On doit au moins avoir le droit de lecture
						portfolioStructure.add(Pair.of(node, rights));
					}
				}
			}
			time5 = System.currentTimeMillis();

		} else if (portfolioDao.isPublic(portfolioUuid)) // Public case, looks like previous query, but with
		{
			List<Node> nodes = nodeDao.getNodesWithResources(portfolioUuid);
			for (Node node : nodes) {
				rights = new GroupRights(true, false, false, false);
				portfolioStructure.add(Pair.of(node, rights));
			}
		}

		time6 = System.currentTimeMillis();

		System.out.println((time1 - time0) + "," + (time2 - time1) + "," + (time3 - time2) + "," + (time4 - time3) + ","
				+ (time5 - time4));
		System.out.println("---- Query Portfolio ----");
		System.out.println("Fetch root: " + (time1 - time0));
		System.out.println("Check rights: " + (time2 - time1));
		System.out.println("Create temp: " + (time3 - time2));
		System.out.println("Fetch rights all/group: " + (time4 - time3));
		System.out.println("Fetch user rights: " + (time5 - time4));
		System.out.println("Actual query: " + (time6 - time5)); //

		return portfolioStructure;
	}

	/**
	 * Récupère les noeuds partages d'un portfolio. C'est séparé car les noeuds ne
	 * provenant pas d'un même portfolio, on ne peut pas les sélectionner rapidement
	 * Autre possibilité serait de garder ce même type de fonctionnement pour une
	 * selection par niveau d'un portfolio.<br>
	 * TODO: A faire un 'benchmark' dessus
	 * 
	 * @param portfolioUuid
	 * @param userId
	 * @param groupId
	 * @param cutoff
	 * @return
	 */
	private List<Pair<Node, GroupRights>> getSharedStructure(String portfolioUuid, Long userId, Long groupId,
			Integer cutoff) {

		List<Pair<Node, GroupRights>> portfolioStructure = new ArrayList<Pair<Node, GroupRights>>();

		if (portfolioDao.hasSharedNodes(portfolioUuid)) {
			List<Node> t_nodes = nodeDao.getSharedNodes(portfolioUuid);
			Map<Integer, Set<String>> t_map_parentid = new HashMap<Integer, Set<String>>();
			Set<String> t_set_parentid = new HashSet<String>();

			for (Node t_node : t_nodes) {
				t_set_parentid.add(t_node.getSharedNodeUuid().toString());
			}

			t_map_parentid.put(0, t_set_parentid);

			/// Les tours de boucle seront toujours <= au nombre de noeud du portfolio.
			int level = 0;
			boolean added = true;
			Set<String> t_struc_parentid_2 = null;
			while (added && (cutoff == null ? true : level < cutoff)) {
				t_struc_parentid_2 = new HashSet<String>();
				for (Node t_node : t_nodes) {
					for (String t_parent_node : t_map_parentid.get(level)) {
						if (t_node.getPortfolio().getId().toString().equals(portfolioUuid)
								&& t_node.getParentNode().getId().toString().equals(t_parent_node)) {
							t_struc_parentid_2.add(t_node.getId().toString());
							break;
						}
					}
				}
				t_map_parentid.put(level + 1, t_struc_parentid_2);
				t_set_parentid.addAll(t_struc_parentid_2);
				added = CollectionUtils.isNotEmpty(t_struc_parentid_2); // On s'arrete quand rien n'a été ajouté
				level = level + 1; // Prochaine étape
			}

			List<Node> nodes = nodeDao.getNodes(new ArrayList<>(t_set_parentid));
			GroupRights rights = null;
			for (Node node : nodes) {
				rights = groupRightsDao.getRightsByIdAndUser(node.getId(), userId);
				if (rights != null && rights.isRead()) { // On doit au moins avoir le droit de lecture
					portfolioStructure.add(Pair.of(node, rights));
				}
			}
		}
		return portfolioStructure;
	}

	public String getPortfolioShared(Long userId) {
		StringBuilder out = new StringBuilder();

		List<Map<String, Object>> portfolios = portfolioDao.getPortfolioShared(userId);
		out.append("<portfolios>");
		Iterator<Map<String, Object>> it = portfolios.iterator();
		Map<String, Object> current = null;
		while (it.hasNext()) {
			current = it.next();
			Long gid = MapUtils.getLong(current, "gid");
			UUID portfolioUuid = (UUID) current.get("portfolio");
			out.append("<portfolio gid='" + gid + "' portfolio='" + portfolioUuid.toString() + "'/>");
		}
		out.append("</portfolios>");
		return out.toString();
	}

	public String getPortfolioByCode(MimeType mimeType, String portfolioCode, Long userId, Long groupId,
			String resources, long substid) throws DoesNotExistException, BusinessException, Exception {
		Portfolio portfolio = portfolioDao.getPortfolioFromNodeCode(portfolioCode);
		if (portfolio == null) {
			throw new DoesNotExistException(Portfolio.class, portfolioCode);
		}

		boolean withResources = BooleanUtils.toBoolean(resources);
		String result = "";

		if (withResources) {
			try {
				return getPortfolio(mimeType, portfolio.getId().toString(), userId, groupId, null, null, null, substid,
						null).toString();
			} catch (MimeTypeParseException e) {
				e.printStackTrace();
			}
		} else {
			result += "<portfolio ";
			result += DomUtils.getXmlAttributeOutput("id", portfolio.getId().toString()) + " ";
			result += DomUtils.getXmlAttributeOutput("root_node_id", portfolio.getRootNode().getId().toString()) + " ";
			result += ">";
			result += nodeManager.getNodeXmlOutput(portfolio.getRootNode().getId().toString(), false, "nodeRes", userId,
					groupId, null, false);
			result += "</portfolio>";

		}
		return result;
	}

	public GroupRights getRightsOnPortfolio(Long userId, Long groupId, String portfolioUuid) {
		GroupRights reponse = new GroupRights();

		try {
			/// modif_user_id => current owner
			Portfolio p = portfolioDao.getPortfolio(portfolioUuid);
			if (p != null) {
				if (p.getModifUserId() == userId)
					// Is the owner
					reponse = new GroupRights(new GroupRightsId(new GroupRightInfo(), null), true, true, true, true,
							true);
				else // General case
					reponse = nodeManager.getRights(userId, groupId, p.getRootNode().getId().toString());
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return reponse;
	}

	/**
	 * Has rights, whether ownership, or given by someone
	 * 
	 * @param userId
	 * @param portfolioUuid
	 * @return
	 */
	public boolean hasRights(Long userId, String portfolioUuid) {
		boolean hasRights = false;

		if (userId != null && portfolioUuid != null) {
			// Évaluer l'appartenance
			Long modif_user_id = portfolioDao.getOwner(portfolioUuid);
			if (Objects.equals(modif_user_id, userId)) {
				hasRights = true;
			} else // Vérifier les autres droits partagés
			{
				List<GroupUser> gu = groupUserDao.getByPortfolioAndUser(portfolioUuid, userId);
				if (CollectionUtils.isNotEmpty(gu)) {
					hasRights = true;
				}
			}
		}
		return hasRights;
	}

	public void removePortfolio(String portfolioUuid, Long userId, Long groupId) throws Exception {
		boolean hasRights = false;

		GroupRights rights = getRightsOnPortfolio(userId, groupId, portfolioUuid);
		if (rights.isDelete() || credentialDao.isAdmin(userId)) {
			hasRights = true;
		}

		if (hasRights) {
			// S'il y a quelque chose de particulier, on s'assure que tout soit bien nettoyé
			// de façon séparée
			List<GroupRightInfo> griList = groupRightInfoDao.getByPortfolioID(portfolioUuid);
			for (java.util.Iterator<GroupRightInfo> it = griList.iterator(); it.hasNext();) {
				groupRightInfoDao.remove(it.next());
				it.remove();
			}

			/// Resources
			List<ResourceTable> rtList = resourceTableDao.getResourcesByPortfolioUUID(portfolioUuid);
			for (java.util.Iterator<ResourceTable> it = rtList.iterator(); it.hasNext();) {
				resourceTableDao.remove(it.next());
				it.remove();
			}

			rtList = resourceTableDao.getContextResourcesByPortfolioUUID(portfolioUuid);
			for (java.util.Iterator<ResourceTable> it = rtList.iterator(); it.hasNext();) {
				resourceTableDao.remove(it.next());
				it.remove();
			}

			rtList = resourceTableDao.getResourcesOfResourceByPortfolioUUID(portfolioUuid);
			for (java.util.Iterator<ResourceTable> it = rtList.iterator(); it.hasNext();) {
				resourceTableDao.remove(it.next());
				it.remove();
			}

			/// Nodes
			List<Node> nodes = nodeDao.getNodes(portfolioUuid);
			for (java.util.Iterator<Node> it = nodes.iterator(); it.hasNext();) {
				nodeDao.remove(it.next());
				it.remove();
			}

			/// Supprimer le portfolio du groupe.
			List<PortfolioGroupMembers> pgmList = portfolioGroupMembersDao.getByPortfolioID(portfolioUuid);
			for (java.util.Iterator<PortfolioGroupMembers> it = pgmList.iterator(); it.hasNext();) {
				portfolioGroupMembersDao.remove(it.next());
				it.remove();
			}

			// Portfolio
			portfolioDao.removeById(UUID.fromString(portfolioUuid));
		}
	}

	public boolean isOwner(Long userId, String portfolioUuid) {
		return portfolioDao.isOwner(userId, portfolioUuid);
	}

	public boolean changePortfolioOwner(String portfolioUuid, long newOwner) {
		return portfolioDao.changePortfolioOwner(portfolioUuid, newOwner);
	}

	public Portfolio changePortfolioConfiguration(String portfolioUuid, Boolean portfolioActive, Long userId)
			throws BusinessException {
		if (!credentialDao.isAdmin(userId)) {
			throw new GenericBusinessException("No admin right");
		}
		return portfolioDao.changePortfolioConfiguration(portfolioUuid, portfolioActive);
	}

	public String getPortfolios(MimeType outMimeType, long userId, long groupId, Boolean portfolioActive,
			long substid, Boolean portfolioProject, String projectId, Boolean countOnly, String search) {
		StringBuilder result = new StringBuilder();
		List<Portfolio> portfolios = portfolioDao.getPortfolios(userId, substid, portfolioActive, portfolioProject);
		result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><portfolios count=\""+portfolios.size()+"\">");
		for( Portfolio p : portfolios ) {
			Node n = p.getRootNode();
			ResourceTable rctx = n.getContextResource();
			ResourceTable rnode = n.getResource();
			ResourceTable rcontent = n.getResResource();
			
			String isOwner = "N";
			String ownerId = n.getModifUserId().toString();
			if( Integer.parseInt(ownerId) == userId )
				isOwner = "Y";
			
			result.append("<portfolio id=\"").append(p.getId().toString());
			result.append("\" root_node_id=\"").append(p.getRootNode().getId().toString());
			result.append("\" owner=\"").append(isOwner);
			result.append("\" ownerid=\"").append(ownerId);
			result.append("\" modified=\"").append(p.getModifDate().toString()).append("\">");
			
				String nodetype = n.getAsmType().toString();
				result.append("<").append(nodetype).append(" id=\"").append(n.getId().toString()).append("\">");
				
				if(!"asmResource".equals(nodetype))
				{
					String metawad = n.getMetadataWad();
					if(metawad!=null && !"".equals(metawad) )
					{
						result.append("<metadata-wad ").append(metawad).append("/>");
					}
					else
						result.append("<metadata-wad/>");
					
					String metaepm = n.getMetadataEpm();
					if(metaepm!=null && !"".equals(metaepm) )
						result.append("<metadata-epm "+metaepm+"/>");
					else
						result.append("<metadata-epm/>");
					
					String meta = n.getMetadata();
					if(meta!=null && !"".equals(meta))
						result.append("<metadata "+meta+"/>");
					else
						result.append("<metadata/>");
					
					String code = n.getCode();
					if(meta!=null && !"".equals(meta))
						result.append("<code>").append(code).append("</code>");
					else
						result.append("<code/>");
					
					String label = n.getLabel();
					if(label!=null && !"".equals(label))
						result.append("<label>").append(label).append("</label>");
					else
						result.append("<label/>");
						
					String descr = n.getDescr();
					if(descr!=null && !"".equals(descr))
						result.append("<description>").append(descr).append("</description>");
					else
						result.append("<description/>");
					
					String semantic = n.getSemantictag();
					if(semantic!=null && !"".equals(semantic))
						result.append("<semanticTag>").append(semantic).append("</semanticTag>");
					else
						result.append("<semanticTag/>");
				
				String nodeUuid = n.getId().toString();
				if( rcontent != null )
				{
					String resresuuid = rcontent.getId().toString();
					if( rcontent != null && resresuuid != null && !"".equals(resresuuid) )
					{
						String xsitype =  rcontent.getXsiType();
						result.append("<asmResource id='").append(resresuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = rcontent.getContent();
						if( resrescont != null && !"".equals(resrescont) )
							result.append(resrescont);
						result.append("</asmResource>");
					}
				}
				
				if( rctx != null )
				{
					String rescontuuid =rctx.getId().toString();
					if( rescontuuid != null && !"".equals(rescontuuid) )
					{
						String xsitype = rctx.getXsiType();
						result.append("<asmResource id='").append(rescontuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = rctx.getContent();
						if( resrescont != null && !"".equals(resrescont) )
							result.append(resrescont);
						result.append("</asmResource>");
					}
				}
				
				if( rnode != null )
				{
					String resnodeuuid = rnode.getId().toString();
					if( resnodeuuid != null && !"".equals(resnodeuuid) )
					{
						String xsitype = rnode.getXsiType().toString();
						result.append("<asmResource id='").append(resnodeuuid).append("' contextid='").append(nodeUuid).append("' xsi_type='").append(xsitype).append("'>");
						String resrescont = rnode.getContent();
						if( resrescont != null && !"".equals(resrescont) )
							result.append(resrescont);
						result.append("</asmResource>");
					}
				}
				result.append("</"+nodetype+">");
				result.append("</portfolio>");
			}

		}
		result.append("</portfolios>");
		return result.toString();
	}

	public boolean rewritePortfolioContent(MimeType inMimeType, MimeType outMimeType, String xmlPortfolio,
			String portfolioUuid, Long userId, Boolean portfolioActive) throws Exception {
		StringBuffer outTrace = new StringBuffer();
		String portfolioModelId = null;

		Portfolio resPortfolio = portfolioDao.getPortfolio(portfolioUuid);
		if (resPortfolio != null) {
			// Le portfolio existe donc on regarde si modèle ou pas
			if (resPortfolio.getModelId() != null) {
				portfolioModelId = resPortfolio.getModelId().toString();
			}
		}

		if (PhpUtil.empty(userId) || !ValidateUtil.isUnsignedId(userId.intValue())) {
			if (resPortfolio != null)
				userId = resPortfolio.getCredential().getId();
		}

		if (xmlPortfolio.length() > 0) {
			Document doc = DomUtils.xmlString2Document(xmlPortfolio, outTrace);

			org.w3c.dom.Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if (rootNode == null) {
				throw new GenericBusinessException("Root Node (portfolio) not found !");
			} else {
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String rootNodeUuid = UUID.randomUUID().toString();
				org.w3c.dom.Node idAtt = rootNode.getAttributes().getNamedItem("id");
				if (idAtt != null) {
					String tempId = idAtt.getNodeValue();
					if (tempId.length() > 0) {
						rootNodeUuid = tempId;
					}
				}

				if (resPortfolio != null) {
					resPortfolio = portfolioDao.merge(resPortfolio);
				} else {
					resPortfolio = portfolioDao.add(rootNodeUuid, null, userId, new Portfolio());
				}
				String resPortfolioUuid = resPortfolio.getId().toString();
				nodeManager.writeNode(rootNode, resPortfolioUuid, portfolioModelId, userId, 0, null, null, false, false,
						true, null, false);
				// On récupère le noeud root généré précédemment et on l'affecte au portfolio.
				resPortfolio.setRootNode(nodeDao.getRootNodeByPortfolio(resPortfolioUuid));
				resPortfolio = portfolioDao.merge(resPortfolio);
			}
		}
		if (resPortfolio != null) {
			portfolioDao.changePortfolioConfiguration(resPortfolio.getId().toString(), portfolioActive);
		}
		return true;
	}

	class right {
		int rd = 0;
		int wr = 0;
		int dl = 0;
		int sb = 0;
		int ad = 0;
		String types = "";
		String rules = "";
		String notify = "";
	}

	public String postPortfolioParserights(String portfolioUuid, Long userId) {

		if (!credentialDao.isAdmin(userId) && !credentialDao.isCreator(userId))
			return "no rights";

		boolean setPublic = false;

		try {

			resolver resolve = new resolver();

			// Sélection des méta-données
			List<Node> nodes = nodeDao.getNodes(portfolioUuid);
			Iterator<Node> it = nodes.iterator();

			DocumentBuilder documentBuilder;
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			while (it.hasNext()) {
				Node current = it.next();
				String uuid = current.getId().toString();
				String meta = current.getMetadataWad();
				// meta = meta.replaceAll("user", login);
				String nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";

				groupright role = resolve.getUuid(uuid);

				try {
					/// parse meta
					InputSource is = new InputSource(new StringReader(nodeString));
					Document doc = documentBuilder.parse(is);

					/// Process attributes
					Element attribNode = doc.getDocumentElement();
					NamedNodeMap attribMap = attribNode.getAttributes();

					String nodeRole;
					org.w3c.dom.Node att = attribMap.getNamedItem("access");
					if (att != null) {
						// if(access.equalsIgnoreCase("public") || access.contains("public"))
						// credential.postGroupRight("all",uuid,Credential.READ,portfolioUuid,userId);
					}
					att = attribMap.getNamedItem("seenoderoles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("showtoroles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();

							right r = role.getGroup(nodeRole);
							r.rd = 0;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("delnoderoles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {

							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("editnoderoles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("submitnoderoles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("seeresroles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.rd = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("delresroles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.dl = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("editresroles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.wr = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					att = attribMap.getNamedItem("submitroles");
					if (att != null) {
						StringTokenizer tokens = new StringTokenizer(att.getNodeValue(), " ");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							right r = role.getGroup(nodeRole);
							r.sb = 1;

							resolve.groups.put(nodeRole, 0L);
						}
					}
					org.w3c.dom.Node actionroles = attribMap.getNamedItem("actionroles");
					if (actionroles != null) {
						/// Format pour l'instant: actionroles="sender:1,2;responsable:4"
						StringTokenizer tokens = new StringTokenizer(actionroles.getNodeValue(), ";");
						while (tokens.hasMoreElements()) {
							nodeRole = tokens.nextElement().toString();
							StringTokenizer data = new StringTokenizer(nodeRole, ":");
							String nrole = data.nextElement().toString();
							String actions = data.nextElement().toString().trim();
							right r = role.getGroup(nrole);
							r.rules = actions;

							resolve.groups.put(nrole, 0L);
						}
					}
					org.w3c.dom.Node menuroles = attribMap.getNamedItem("menuroles");
					if (menuroles != null) {
						/// Pour les différents items du menu
						StringTokenizer menuline = new StringTokenizer(menuroles.getNodeValue(), ";");

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
								for (int i = 0; i < roles.length; ++i)
									resolve.groups.put(roles[i].trim(), 0L);
							}
						}
					}
					org.w3c.dom.Node notifyroles = attribMap.getNamedItem("notifyroles");
					if (notifyroles != null) {
						/// Format pour l'instant: notifyroles="sender responsable"
						StringTokenizer tokens = new StringTokenizer(notifyroles.getNodeValue(), " ");
						String merge = "";
						if (tokens.hasMoreElements())
							merge = tokens.nextElement().toString().trim();
						while (tokens.hasMoreElements())
							merge += "," + tokens.nextElement().toString().trim();
						role.setNotify(merge);
					}

					if (portfolioDao.isPublic(portfolioUuid))
						setPublic = true;
//						/*
					meta = current.getMetadata();
					nodeString = "<?xml version='1.0' encoding='UTF-8' standalone='no'?><transfer " + meta + "/>";
					is = new InputSource(new StringReader(nodeString));
					doc = documentBuilder.parse(is);
					attribNode = doc.getDocumentElement();
					attribMap = attribNode.getAttributes();
					org.w3c.dom.Node publicatt = attribMap.getNamedItem("public");
					if (publicatt != null && "Y".equals(publicatt.getNodeValue()))
						setPublic = true;
					// */
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			/// On insère les données pré-compile
			Iterator<String> entries = resolve.groups.keySet().iterator();

			// Crée les groupes, ils n'existent pas
			GroupInfo gi = new GroupInfo();
			GroupRightInfo gri = new GroupRightInfo();

			while (entries.hasNext()) {
				String label = entries.next();
				gri.setOwner(1L);
				gri.setLabel(label);
				gri.setChangeRights(false);
				gri.setPortfolio(new Portfolio(UUID.fromString(portfolioUuid)));

				groupRightInfoDao.persist(gri);
				Long grid = gri.getId();
				resolve.groups.put(label, grid);

				gi.setGroupRightInfo(gri);
				gi.setOwner(1L);
				gi.setLabel(label);
				groupInfoDao.persist(gi);
			}

			/// Ajout des droits des noeuds
			GroupRights groupRights = null;

			Iterator<Entry<String, groupright>> rights = resolve.resolve.entrySet().iterator();
			while (rights.hasNext()) {
				Entry<String, groupright> entry = rights.next();
				String uuid = entry.getKey();
				groupright gr = entry.getValue();

				Iterator<Entry<String, right>> rightiter = gr.rights.entrySet().iterator();
				while (rightiter.hasNext()) {
					Entry<String, right> rightelem = rightiter.next();
					String group = rightelem.getKey();
					long grid = resolve.groups.get(group);
					right rightval = rightelem.getValue();
					groupRights = new GroupRights();
					groupRights.setId(new GroupRightsId());
					groupRights.setGroupRightInfo(new GroupRightInfo(grid));
					groupRights.setGroupRightsId(UUID.fromString(uuid));
					groupRights.setRead(BooleanUtils.toBoolean(rightval.rd));
					groupRights.setWrite(BooleanUtils.toBoolean(rightval.wr));
					groupRights.setDelete(BooleanUtils.toBoolean(rightval.dl));
					groupRights.setSubmit(BooleanUtils.toBoolean(rightval.sb));
					groupRights.setAdd(BooleanUtils.toBoolean(rightval.ad));
					groupRights.setTypesId(rightval.types);
					groupRights.setRulesId(rightval.rules);
					groupRights.setNotifyRoles(rightval.notify);
					groupRightsDao.persist(groupRights);
				}
			}

			// Créer le groupe de base
			Long groupid = securityManager.addRole(portfolioUuid, "all", userId);

			/// Finalement on crée un role designer
			groupid = securityManager.addRole(portfolioUuid, "designer", userId);

			Credential usercred = credentialDao.findById(userId);
			GroupInfo groupobj = groupInfoDao.getGroupsByRole(portfolioUuid, "designer").get(0);
			
			groupUserDao.addUserInGroup(usercred, groupobj);

			// Maj. de la date
			portfolioDao.updateTime(portfolioUuid);

			// Rendre le portfolio public si nécessaire
			if (setPublic)
				groupManager.setPublicState(userId, portfolioUuid, setPublic);
		} catch (Exception e) {
			logger.error("MESSAGE: " + e.getMessage() + " " + e.getLocalizedMessage());
		}

		return portfolioUuid;
	}

	public String addPortfolio(MimeType inMimeType, MimeType outMimeType, String xmlPortfolio, long userId,
			long groupId, String portfolioModelId, long substid, boolean parseRights, String projectName)
			throws BusinessException, Exception {
		if (!credentialDao.isAdmin(userId) && !credentialDao.isCreator(userId))
			throw new GenericBusinessException("FORBIDDEN : No admin right");

		StringBuffer outTrace = new StringBuffer();

		// Si le modèle est renseigné, on ignore le XML poste et on récupère le contenu
		// du modèle a la place
		// FIXME Inutilisé, nous instancions / copions un portfolio
		if (portfolioModelId != null)
			xmlPortfolio = getPortfolio(inMimeType, portfolioModelId, userId, groupId, null, null, null, substid, null)
					.toString();

		Portfolio portfolio = null;
		if (xmlPortfolio.length() > 0) {
			Document doc = DomUtils.xmlString2Document(xmlPortfolio, outTrace);

			// Vérifier si le code est déjà utilisé par un portfolio.
			XPath xPath = XPathFactory.newInstance().newXPath();
			String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
			NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

			if (nodelist.getLength() > 0) {
				String code = nodelist.item(0).getTextContent();
				if (projectName != null) {
					// Find if it contains a project name
					int dot = code.indexOf(".");
					if (dot < 0) // Doesn't exist, add it
						code = projectName + "." + code;
					else // Replace
						code = projectName + code.substring(dot);
				}

				// Simple query
				if (nodeDao.isCodeExist(code, null)) {
					throw new GenericBusinessException("CONFLICT : Existing code.");
				}
				nodelist.item(0).setTextContent(code);
			}

			org.w3c.dom.Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
			if (rootNode == null) {
				throw new Exception("Root Node (portfolio) not found !");
			} else {
				rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

				String uuid = UUID.randomUUID().toString();

				portfolio = portfolioDao.add(uuid, null, userId, new Portfolio());
				nodeManager.writeNode(rootNode, portfolio.getId().toString(), portfolioModelId, userId, 0, uuid, null,
						false, false, false, null, parseRights);
				// On récupère le noeud root généré précédemment et on l'affecte au portfolio.
				portfolio.setRootNode(nodeDao.getRootNodeByPortfolio(portfolio.getId().toString()));
			}
		}

		portfolio.setActive(1);
		if (StringUtils.isNotEmpty(portfolioModelId))
			portfolio.setModelId(UUID.fromString(portfolioModelId));

		portfolio = portfolioDao.merge(portfolio);

		/// Si nous instancions, nous n'avons pas besoin du rôle de concepteur
		long roleId = securityManager.addRole(portfolio.getId().toString(), "all", userId);
		/// Créer groupe 'designer', 'all' est mis avec ce qui est spécifique dans le
		/// XML reçu.
		roleId = securityManager.addRole(portfolio.getId().toString(), "designer", userId);

		Credential usercred = credentialDao.findById(userId);
		GroupInfo groupobj = groupInfoDao.getGroupsByRole(portfolio.getId().toString(), "designer").get(0);

		/// Ajoute la personne dans ce groupe
		groupUserDao.addUserInGroup(usercred, groupobj);

		String result = "<portfolios>";
		result += "<portfolio ";
		result += DomUtils.getXmlAttributeOutput("id", portfolio.getId().toString()) + " ";
		result += "/>";
		result += "</portfolios>";
		return result;
	}

	public String importZippedPortfolio(MimeType mimeType, MimeType mimeType2, String path, String userName,
			InputStream inputStream, Long userId, Long groupId, String modelId, Long credentialSubstitutionId,
			boolean parseRights, String projectName) throws BusinessException, FileNotFoundException, Exception {
		if (!credentialDao.isAdmin(userId) && !credentialDao.isCreator(userId))
			throw new GenericBusinessException("403 FORBIDDEN : No admin right");

		if (projectName == null)
			projectName = "";
		else
			projectName = projectName.trim();

		DataInputStream inZip = new DataInputStream(inputStream);
		// Parse the request
		System.out.println(inputStream);

		String filename;
		String[] xmlFiles;
		String[] allFiles;
		byte[] buff = new byte[0x100000]; // 1MB buffer

		String outsideDir = path.substring(0, path.lastIndexOf(File.separator)) + "_files" + File.separator;
		File outsideDirectoryFile = new File(outsideDir);
		System.out.println(outsideDir);
		// if the directory does not exist, create it
		if (!outsideDirectoryFile.exists()) {
			outsideDirectoryFile.mkdir();
		}

		// Création de l'archive au format zip.
		String portfolioUuidPreliminaire = UUID.randomUUID().toString();
		filename = outsideDir + "xml_" + portfolioUuidPreliminaire + ".zip";
		FileOutputStream outZip = new FileOutputStream(filename);

		int len;

		while ((len = inZip.read(buff)) != -1) {
			outZip.write(buff, 0, len);
		}

		inZip.close();
		outZip.close();

		// -- unzip --
		fileManager.unzip(filename, outsideDir + portfolioUuidPreliminaire + File.separator);

		// Unzip just the next zip level. I hope there will be no zipped documents...
		String[] zipFiles = fileManager.findFiles(outsideDir + portfolioUuidPreliminaire + File.separator, "zip");
		for (int i = 0; i < zipFiles.length; ++i) {
			fileManager.unzip(zipFiles[i], outsideDir + portfolioUuidPreliminaire + File.separator);
		}

		xmlFiles = fileManager.findFiles(outsideDir + portfolioUuidPreliminaire + File.separator, "xml");
		allFiles = fileManager.findFiles(outsideDir + portfolioUuidPreliminaire + File.separator, null);

		////// Lecture du fichier de portfolio
		StringBuffer outTrace = new StringBuffer();
		//// Importation du portfolio
		// --- Lecture fichier XML ----
		///// Pour associer l'ancien uuid -> nouveau, pour les fichiers
		Map<String, String> resolve = new HashMap<String, String>();
		Portfolio portfolio = null;
		boolean hasLoaded = false;
		try {
			for (int i = 0; i < xmlFiles.length; i++) {
				String xmlFilepath = xmlFiles[i];
				String xmlFilename = xmlFilepath.substring(xmlFilepath.lastIndexOf(File.separator));
				if (xmlFilename.contains("_"))
					continue; // Case when we add an XML in the portfolio

				BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(xmlFilepath), "UTF8"));
				String line;
				StringBuilder sb = new StringBuilder();

				while ((line = br.readLine()) != null) {
					sb.append(line.trim());
				}
				br.close();
				String xml = "?";
				xml = sb.toString();

				portfolio = new Portfolio();

				if (xml.contains("<portfolio")) // Le porfolio (peux mieux faire)
				{
					Document doc = DomUtils.xmlString2Document(xml, outTrace);

					// Find code
					/// Cherche si on a deje envoye quelque chose
					XPath xPath = XPathFactory.newInstance().newXPath();
					String filterRes = "//*[local-name()='asmRoot']/*[local-name()='asmResource']/*[local-name()='code']";
					NodeList nodelist = (NodeList) xPath.compile(filterRes).evaluate(doc, XPathConstants.NODESET);

					if (nodelist.getLength() > 0) {
						String code = nodelist.item(0).getTextContent();

						if (!"".equals(projectName)) // If a new name has been specified
						{
							// Find if it contains a project name
							int dot = code.indexOf(".");
							if (dot > 0)
								code = projectName + code.substring(dot);
							else // If no dot, it's a project, skip it
								continue;

							// Check if new code exists
							if (nodeDao.isCodeExist(code, null))
								throw new GenericBusinessException("409 Conflict : Existing code.");

							// Replace content
							nodelist.item(0).setTextContent(code);
						} else // Otherwise, check if it exists
						{
							// Simple query
							if (nodeDao.isCodeExist(code, null))
								throw new GenericBusinessException("409 Conflict : Existing code.");
						}
					}

					// Check if it needs replacing
					org.w3c.dom.Node rootNode = (doc.getElementsByTagName("portfolio")).item(0);
					if (rootNode == null)
						throw new Exception("Root Node (portfolio) not found !");
					else {
						rootNode = (doc.getElementsByTagName("asmRoot")).item(0);

						String uuid = UUID.randomUUID().toString();
						portfolioDao.add(uuid, null, userId, portfolio);
						nodeManager.writeNode(rootNode, portfolio.getId().toString(), null, userId, 0, uuid, null,
								false, false, false, resolve, parseRights);
						// On récupère le noeud root généré précédemment et on l'affecte au portfolio.
						portfolio.setRootNode(nodeDao.getRootNodeByPortfolio(portfolio.getId().toString()));
					}

					portfolio.setActive(1);
					portfolioDao.merge(portfolio);

					/// Create base group
					Long groupid = securityManager.addRole(portfolio.getId().toString(), "all", userId);
					/// Finalement on crée un rôle de designer
					groupid = securityManager.addRole(portfolio.getId().toString(), "designer", userId);

					Credential usercred = credentialDao.findById(userId);
					GroupInfo groupobj = groupInfoDao.getGroupsByRole(portfolio.getId().toString(), "designer").get(0);

					/// Ajoute la personne dans ce groupe
					groupUserDao.addUserInGroup(usercred, groupobj);

					hasLoaded = true;
				}
			}
		} catch (Exception e) {
			throw e;
		}

		if (hasLoaded)
			for (int i = 0; i < allFiles.length; i++) {
				String fullPath = allFiles[i];
				String tmpFileName = fullPath.substring(fullPath.lastIndexOf(File.separator) + 1);

				if (!tmpFileName.contains("_"))
					continue; // We want ressources now, they have '_' in their name
				int index = tmpFileName.indexOf("_");
				if (index == -1)
					index = tmpFileName.indexOf(".");
				int last = tmpFileName.lastIndexOf(File.separator);
				if (last == -1)
					last = 0;
				String uuid = tmpFileName.substring(last, index);

				String lang;
				try {
					lang = tmpFileName.substring(index + 1, index + 3);

					if ("un".equals(lang)) // Hack sort of fixing previous implementation
						lang = "en";
				} catch (Exception ex) {
					lang = "";
				}

				// Attention on initialise la ligne file
				// avec l'UUID d'origine de l'asmContext parent
				// Il sera mis e jour avec l'UUID asmContext final dans writeNode
				try {
					String resolved = resolve.get(uuid); /// New uuid
					String sessionval = Tools.passwdGen(24, "RANDOM");
					// session.getId()
					// FIX ... there is no session id in RESTFUL webServices so generate a mocked
					// one in place
					File file = new File(fullPath);
					String backend = configurationDao.get("backendserver");

					if (resolved != null) {
						/// Have to send it in FORM, compatibility with regular file posting
						fileManager.rewriteFile(sessionval, backend, userName, resolved, lang, file);
					}
				} catch (Exception ex) {
					// Le nom du fichier ne commence pas par un UUID,
					// ce n'est donc pas une ressource
					ex.printStackTrace();
				}
			}

		File zipfile = new File(filename);
		zipfile.delete();
		File zipdir = new File(outsideDir + portfolioUuidPreliminaire + File.separator);
		zipdir.delete();

		return portfolio.getId().toString();
	}

	public String instanciatePortfolio(MimeType mimeType, String portfolioId, String srccode, String tgtcode, Long id,
			int groupId, boolean copyshared, String groupname, boolean setOwner) {
		// TODO Auto-generated method stub
		return null;
	}

	public int addPortfolioInGroup(String portfolioUuid, Long portfolioGroupId, String label, Long userId) {
		try {
			PortfolioGroup pg = portfolioGroupDao.findById(portfolioGroupId);
			Portfolio p = null;
			if (label != null) {
				pg.setLabel(label);
				pg = portfolioGroupDao.merge(pg);
			} else {
				if (!StringUtils.equalsIgnoreCase(pg.getType(), "PORTFOLIO"))
					return 1;

				p = portfolioDao.findById(UUID.fromString(portfolioUuid));

				PortfolioGroupMembers pgm = new PortfolioGroupMembers(new PortfolioGroupMembersId());
				pgm.setPortfolio(p);
				pgm.setPortfolioGroup(pg);
				portfolioGroupMembersDao.persist(pgm);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return 1;
		}
		return 0;
	}

	public Long getPortfolioGroupIdFromLabel(String groupLabel, Long userId) {
		return portfolioGroupDao.getPortfolioGroupIdFromLabel(groupLabel);
	}

	public String getPortfolioGroupList() {
		class TreeNode {
			String nodeContent;
			int nodeId;
			List<TreeNode> childs = new ArrayList<TreeNode>();
		}
		class ProcessTree {
			public void reconstruct(StringBuilder data, TreeNode tree) {
				String nodeData = tree.nodeContent;
				data.append(nodeData); // Add current node content
				for (int i = 0; i < tree.childs.size(); ++i) {
					TreeNode child = tree.childs.get(i);
					reconstruct(data, child);
				}
				// Close node tag
				data.append("</group>");
			}
		}

		StringBuilder result = new StringBuilder();
		result.append("<groups>");
		try {
			List<PortfolioGroup> pgList = portfolioGroupDao.findAll();

			List<TreeNode> trees = new ArrayList<TreeNode>();
			Map<Long, TreeNode> resolve = new HashMap<Long, TreeNode>();
			ProcessTree pf = new ProcessTree();

			StringBuilder currNode = new StringBuilder();
			for (PortfolioGroup pg : pgList) {
				currNode.setLength(0);
				String pgStr = String.valueOf(pg.getId());
				String type = pg.getType();
				currNode.append("<group type='").append(type.toLowerCase()).append("' id=\"");
				currNode.append(pgStr);
				currNode.append("\"><label>");
				currNode.append(pg.getLabel());
				currNode.append("</label>");
				// group tag will be closed at reconstruction

				TreeNode currTreeNode = new TreeNode();
				currTreeNode.nodeContent = currNode.toString();
				currTreeNode.nodeId = Integer.parseInt(pgStr);
				PortfolioGroup parent = null;
				if (pg.getParent() != null) {
					parent = pg.getParent().getParent();
				}
				resolve.put(Long.valueOf(currTreeNode.nodeId), currTreeNode);
				if (parent != null && !PhpUtil.empty(parent.getId())) {
					TreeNode parentTreeNode = resolve.get(parent.getId());
					parentTreeNode.childs.add(currTreeNode);
				} else // Top level groups
				{
					trees.add(currTreeNode);
				}
			}

			/// Go through top level parent and reconstruct each tree
			for (int i = 0; i < trees.size(); ++i) {
				TreeNode topNode = trees.get(i);
				pf.reconstruct(result, topNode);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		result.append("</groups>");

		return result.toString();
	}

	public String getPortfolioGroupListFromPortfolio(String portfolioUuid) {
		List<PortfolioGroupMembers> pgmList = portfolioGroupMembersDao.getByPortfolioID(portfolioUuid);
		final StringBuilder result = new StringBuilder();
		result.append("<portfolio>");
		for (PortfolioGroupMembers pgm : pgmList) {
			if (pgm.getPortfolioGroup() != null && !PhpUtil.empty(pgm.getPortfolioGroup().getId())) {
				result.append("<group");
				result.append(" id=\"");
				result.append(pgm.getPortfolioGroup().getId());
				result.append("\">");
				result.append(pgm.getPortfolioGroup().getLabel());
				result.append("</group>");
			}
		}
		result.append("</portfolio>");
		return result.toString();
	}

	public Long addPortfolioGroup(String groupname, String type, Long parentId, Long userId) {
		Long groupid = -1L;
		boolean isOK = true;
		try {
			// Vérifier si le parent existe.
			if (parentId != null && !portfolioGroupDao.exists(parentId, "GROUP")) {
				isOK = false;
			}

			if (isOK) {
				PortfolioGroup pg = new PortfolioGroup();
				pg.setLabel(groupname);
				pg.setType(type);
				if (parentId != null)
					pg.setParent(new PortfolioGroup(parentId));

				portfolioGroupDao.persist(pg);
				groupid = pg.getId();
			}

		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}

		return groupid;

	}

	public String getRoleByPortfolio(MimeType mimeType, String role, String portfolioUuid, Long userId) {
		GroupRightInfo gri = groupRightInfoDao.getByPortfolioAndLabel(portfolioUuid, role);
		Long grid = null;
		if (gri != null) {
			grid = gri.getId();
		} else {
			return "Le grid n'existe pas";
		}
		return "grid = " + grid;
	}

	public String getRolesByPortfolio(String portfolioUuid, Long userId) {
		GroupRights rights = getRightsOnPortfolio(userId, 0L, portfolioUuid);
		if (!rights.isRead())
			return null;

		List<GroupInfo> giList = groupInfoDao.getByPortfolio(portfolioUuid);

		String result = "<groups>";
		for (GroupInfo gi : giList) {
			result += "<group ";
			result += DomUtils.getXmlAttributeOutput("id", gi.getId().toString()) + " ";
			result += DomUtils.getXmlAttributeOutput("templateId", gi.getGroupRightInfo().getId().toString()) + " ";
			result += ">";
			result += DomUtils.getXmlElementOutput("groupid", gi.getId().toString()) + " ";
			result += DomUtils.getXmlElementOutput("groupname", gi.getLabel());
			result += DomUtils.getXmlElementOutput("roleid", gi.getGroupRightInfo().getId().toString()) + " ";
			result += DomUtils.getXmlElementOutput("rolename", gi.getGroupRightInfo().getLabel()) + " ";
			result += "</group>";
		}
		result += "</groups>";

		return result;
	}

	public String getGroupRightsInfos(Long userId, String portfolioId) throws BusinessException {
		if (!credentialDao.isAdmin(userId))
			throw new GenericBusinessException("403 FORBIDDEN : no admin right");

		List<GroupRightInfo> resList = groupRightInfoDao.getByPortfolioAndUser(UUID.fromString(portfolioId), userId);
		String result = "<groupRightsInfos>";
		for (GroupRightInfo res : resList) {
			result += "<groupRightInfo ";
			result += DomUtils.getXmlAttributeOutput("grid", res.getId().toString()) + " ";
			result += ">";
			result += "<label>" + res.getLabel() + "</label>";
			result += "<owner>" + String.valueOf(res.getOwner()) + "</owner>";
			result += "</groupRightInfo>";
		}

		result += "</groupRightsInfos>";
		return result;
	}

	public String addRoleInPortfolio(Long userId, String portfolioUuid, String data) throws BusinessException {
		if (!credentialDao.isAdmin(userId) && !portfolioDao.isOwner(userId, portfolioUuid))
			throw new GenericBusinessException("FORBIDDEN 403 : No admin right");

		String value = "erreur";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(data));
			document = documentBuilder.parse(is);
		} catch (Exception e) {
			e.printStackTrace();
		}

		/// Problème de parsage
		if (document == null)
			return value;

		try {
			Element labelNode = document.getDocumentElement();
			String label = null;

			if (labelNode != null) {
				org.w3c.dom.Node labelText = labelNode.getFirstChild();
				if (labelText != null)
					label = labelText.getNodeValue();
			}

			if (label == null)
				return value;
			/// Création du groupe de droit

			Long grid = 0L;
			final GroupRightInfo gri = new GroupRightInfo();
			gri.setOwner(userId);
			gri.setLabel(label);
			gri.setPortfolio(new Portfolio(UUID.fromString(portfolioUuid)));

			try {
				groupRightInfoDao.persist(gri);
				grid = gri.getId();
			} catch (Exception e) {
			}

			labelNode.setAttribute("id", Long.toString(grid));

			/// Récupère les données avec identifiant mis à jour
			StringWriter stw = new StringWriter();
			Transformer serializer = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult stream = new StreamResult(stw);
			serializer.transform(source, stream);
			value = stw.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return value;
	}

	public String copyPortfolio(MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, Long userId,
			boolean setOwner) throws Exception {
		Portfolio originalPortfolio = null;
		String newPortfolioUuid = null;
		try {
			/// le code source est OK ?
			if (srcCode != null) {
				// Retrouver le portfolio à partir du code source
				originalPortfolio = portfolioDao.getPortfolioFromNodeCode(srcCode);
				if (originalPortfolio != null)
					// Portfolio uuid
					portfolioUuid = originalPortfolio.getId().toString();
			}

			if (portfolioUuid == null)
				return "Error: no portofolio selected";

			//////////////////////////////
			/// Copie de la structure ///
			/////////////////////////////
			final Map<Node, Node> nodes = new HashMap<Node, Node>();

			// Récupération des noeuds du portfolio à copier.
			final List<Node> originalNodeList = nodeDao.getNodes(portfolioUuid);
			final List<Node> copiedNodeList = new ArrayList<Node>(originalNodeList.size());
			Node copy = null, original = null, rootNodeCopy = null;
			/// Copie des noeuds -- structure du portfolio
			for (Iterator<Node> it = originalNodeList.iterator(); it.hasNext();) {
				original = it.next();
				copy = new Node(original);
				if (setOwner) {
					copy.setModifUserId(userId);
				} else {
					copy.setModifUserId(1L); // FIXME hard-coded root userid
				}

				////////////////////////////
				/// Copie des ressources///
				///////////////////////////
				if (copy.getResource() != null) {
					if (setOwner) {
						copy.getResource().setModifUserId(userId);
					} else {
						copy.getResource().setModifUserId(1L);
					}
					resourceTableDao.persist(copy.getResource());
				}
				if (copy.getResResource() != null) {
					// Mise a jour du code dans le contenu du noeud.
					if (StringUtils.equalsIgnoreCase(copy.getAsmType(), "asmRoot")) {
						copy.getResResource().setContent(
								StringUtils.replace(copy.getResResource().getContent(), copy.getCode(), newCode));

					}
					if (setOwner) {
						copy.getResResource().setModifUserId(userId);
					} else {
						copy.getResResource().setModifUserId(1L);
					}
					resourceTableDao.persist(copy.getResResource());
				}
				if (copy.getContextResource() != null) {
					if (setOwner) {
						copy.getContextResource().setModifUserId(userId);
					} else {
						copy.getContextResource().setModifUserId(1L);
					}
					resourceTableDao.persist(copy.getContextResource());
				}

				// Mise à jour du code dans le code interne de la BD.
				if (StringUtils.equalsIgnoreCase(copy.getAsmType(), "asmRoot")) {
					copy.setCode(newCode);
					rootNodeCopy = copy;
				}

				nodeDao.persist(copy);
				copiedNodeList.add(copy);
				nodes.put(original, copy);
			}

			/// Ajout du portfolio en base.
			Portfolio portfolioCopy = new Portfolio(originalPortfolio);
			portfolioCopy.setRootNode(rootNodeCopy);
			portfolioDao.persist(portfolioCopy);
			newPortfolioUuid = portfolioCopy.getId().toString();

			Entry<Node, Node> entry = null;
			Node key = null;
			Node value = null;
			Node searchedNode = new Node();
			for (Iterator<Entry<Node, Node>> it = nodes.entrySet().iterator(); it.hasNext();) {
				entry = it.next();
				key = entry.getKey();
				value = entry.getValue();
				/// Assignation des nouveaux parents
				if (key.getParentNode() != null) {
					value.setParentNode(nodes.get(key.getParentNode()));
				}
				/// Mise à jour de la liste des enfants
				/// L'ordre determine le rendu visuel final du XML.
				if (key.getChildrenStr() != null) {
					String[] children = StringUtils.split(key.getChildrenStr(), ",");
					String[] childrenCopies = new String[children.length];
					for (int i = 0; i < children.length; i++) {
						searchedNode.setId(UUID.fromString(children[i]));
						copy = nodes.get(searchedNode);
						childrenCopies[i] = copy.getId().toString();
					}
					value.setChildrenStr(StringUtils.join(childrenCopies, ","));
				}
				/// Liaison des noeuds copiés au nouveau portfolio.
				value.setPortfolio(portfolioCopy);
				nodeDao.merge(value);
			}

			/// Finalement on crée un role designer
			Long groupid = securityManager.addRole(newPortfolioUuid, "designer", userId);

			Credential usercred = credentialDao.findById(userId);
			GroupInfo groupobj = groupInfoDao.getGroupsByRole(newPortfolioUuid, "designer").get(0);

			/// Ajoute la personne dans ce groupe
			groupUserDao.addUserInGroup(usercred, groupobj);

			/// Force 'all' role creation
			groupid = securityManager.addRole(newPortfolioUuid, "all", userId);

			/// Check base portfolio's public state and act accordingly
			if (portfolioDao.isPublic(portfolioUuid))
				groupManager.setPublicState(userId, newPortfolioUuid, true);

		} catch (Exception e) {
			newPortfolioUuid = "erreur: " + e.getMessage();
		}
		return newPortfolioUuid;
	}

	public String getPortfolioUuidFromNode(String nodeUuid) {
		UUID res = portfolioDao.getPortfolioUuidFromNode(nodeUuid);
		return res != null ? res.toString() : null;
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public void transferPortfolioGroupMembersTable(Connection con, Map<String, String> portIds, Map<Long, Long> pgIds)
			throws SQLException {

		ResultSet res = portfolioGroupMembersDao.getMysqlPortfolioGroupMembers(con);
		PortfolioGroupMembers pgm = new PortfolioGroupMembers();
		try {
			while (res.next()) {
				pgm.setId(new PortfolioGroupMembersId(new PortfolioGroup(pgIds.get(res.getLong("pg"))),
						new Portfolio(UUID.fromString(portIds.get(res.getString("portfolio_id"))))));
				portfolioGroupMembersDao.merge(pgm);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public Map<String, String> transferPortfolioTable(Connection con, Map<Long, Long> userIds) throws SQLException {
		ResultSet res_portfolio = portfolioDao.getMysqlPortfolios(con);
		Portfolio p = null;
		Map<String, String> portfoliosIds = new HashMap<String, String>();

		try {
			while (res_portfolio.next()) {
				try {
					p = new Portfolio();

					if (res_portfolio.getLong("user_id") != 0) {
						p.setCredential(credentialDao.findById(userIds.get(res_portfolio.getLong("user_id"))));
					}
					p.setModelId(UUID.fromString(res_portfolio.getString("model_id")));
					p.setModifUserId(userIds.get(res_portfolio.getLong("modif_user_id")));
					p.setModifDate(res_portfolio.getDate("modif_date"));
					p.setActive(res_portfolio.getInt("active"));
					portfolioDao.persist(p);
					String rootNodeUuid = res_portfolio.getString("root_node_uuid");
					if (StringUtils.isNotEmpty(rootNodeUuid)) {
						p.setRootNode(new Node(UUID.fromString(rootNodeUuid)));
					}
					portfoliosIds.put(res_portfolio.getString("portfolio_id"), p.getId().toString());
				} catch (DoesNotExistException e) {
					e.printStackTrace();
				} catch (Exception e) {
					System.err.println(e.getMessage());
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return portfoliosIds;
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public Map<Long, Long> transferPortfolioGroupTable(Connection con) throws SQLException {
		ResultSet res = portfolioGroupDao.findAll("portfolio_group", con);
		PortfolioGroup pg = null;
		Map<Long, Long> pgIds = new HashMap<Long, Long>();
		while (res.next()) {
			pg = new PortfolioGroup();
			pg.setLabel(res.getString("label"));
			pg.setType(res.getString("type"));
//			
			pg = portfolioGroupDao.merge(pg);
			pgIds.put(res.getLong("pg"), pg.getId());
		}
		return pgIds;
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public void transferParentPortfolioGroup(Connection con, Map<Long, Long> pgIds) throws SQLException {
		ResultSet res = portfolioGroupDao.findAll("portfolio_group", con);
		PortfolioGroup pg = null;
		try {
			while (res.next()) {
				try {
					pg = portfolioGroupDao.findById(pgIds.get(res.getLong("pg")));
					final long pg_parent = res.getLong("pg_parent");
					if (pg_parent != 0) {
						pg.setParent(portfolioGroupDao.findById(pgIds.get(pg_parent)));
					}
					portfolioGroupDao.merge(pg);
				} catch (DoesNotExistException e) {
					e.printStackTrace();
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public void removePortfolios() {
		portfolioDao.removeAll();
	}

	@Override
	@Transactional(isolation = Isolation.DEFAULT, propagation = Propagation.REQUIRES_NEW)
	public void removePortfolioGroups() {
		portfolioGroupMembersDao.removeAll();
		portfolioGroupDao.removeAll();
	}
}
