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

package eportfolium.com.karuta.business.contract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.parsers.ParserConfigurationException;

import org.springframework.util.MimeType;

import eportfolium.com.karuta.model.bean.GroupRights;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.model.exception.DoesNotExistException;

public interface NodeManager {

	String getNode(MimeType outMimeType, UUID nodeId, boolean withChildren, Long userId, Long groupId, String label,
			Integer cutoff) throws DoesNotExistException, BusinessException, Exception;

	String getChildNodes(String parentNodeCode, String parentSemtag, String semtag) throws Exception;

	GroupRights getRights(Long userId, Long groupId, UUID nodeId);

	void resetRights(List<Node> children) throws ParserConfigurationException;

	/**
	 * <node uuid=""> <role name=""> <right RD="" WR="" DL="" />
	 * <action>reset</action> </role> </node> ====== <portfolio uuid="">
	 * <xpath>XPATH</xpath> <role name=""> <right RD="" WR="" DL="" />
	 * <action>reset</action> </role> </portfolio> ====== <portfoliogroup name="">
	 * <xpath>XPATH</xpath> <role name=""> <right RD="" WR="" DL="" />
	 * <action>reset</action> </role> </portfoliogroup>
	 * 
	 * @param xmlNode
	 * @param userId
	 * @param subId
	 * @param label
	 * @throws BusinessException
	 * @throws Exception
	 */
	void changeRights(String xmlNode, Long id, Long credentialSubstitutionId, String label)
			throws BusinessException, Exception;

	String changeRights(Long userId, UUID nodeId, String role, GroupRights rights) throws BusinessException;

	UUID getPortfolioIdFromNode(Long userId, UUID nodeId) throws DoesNotExistException, BusinessException;

	String getNodeXmlOutput(UUID nodeId, boolean withChildren, String withChildrenOfXsiType, Long userId,
			Long groupId, String label, boolean checkSecurity);

	String getNodeBySemanticTag(MimeType textXml, UUID portfolioId, String semantictag, Long userId, Long groupId)
			throws BusinessException;

	String getNodesBySemanticTag(MimeType outMimeType, Long userId, Long groupId, UUID portfolioId,
			String semanticTag) throws BusinessException;

	/**
	 * forcedParentUuid permet de forcer l'uuid parent, independamment de l'attribut
	 * du noeud fourni
	 *
	 * @param node
	 * @param portfolioUuid
	 * @param portfolioModelId
	 * @param userId
	 * @param ordrer
	 * @param forcedId
	 * @param forcedParentId
	 * @param sharedResParent
	 * @param sharedNodeResParent
	 * @param rewriteId
	 * @param resolve
	 * @param parseRights
	 * @return
	 * @throws BusinessException
	 */
	UUID writeNode(org.w3c.dom.Node node, UUID portfolioId, UUID portfolioModelId, Long userId, int ordrer,
					 UUID forcedId, UUID forcedParentId, boolean sharedResParent, boolean sharedNodeResParent,
					 boolean rewriteId, Map<UUID, UUID> resolve, boolean parseRights) throws BusinessException;

	boolean isCodeExist(String code, UUID uuid);

	boolean isCodeExist(String code);

	String executeMacroOnNode(long userId, UUID nodeId, String macroName) throws BusinessException;

	String getNodeMetadataWad(MimeType mimeType, UUID nodeId, Long userId, Long groupId)
			throws DoesNotExistException, BusinessException;

	Integer changeNode(MimeType inMimeType, UUID nodeId, String xmlNode, Long userId, Long groupId)
			throws Exception;

	void removeNode(UUID nodeId, Long userId, long groupId) throws BusinessException;

	long getRoleByNode(Long userId, UUID nodeUuid, String role) throws BusinessException;

	String changeNodeMetadataWad(MimeType mimeType, UUID nodeId, String xmlMetawad, Long userId, Long groupId)
			throws Exception;

	boolean changeParentNode(Long userid, UUID id, UUID parentId) throws BusinessException;

	Long moveNodeUp(UUID nodeId) throws BusinessException;

	String addNodeFromModelBySemanticTag(MimeType inMimeType, UUID nodeId, String semanticTag, Long userId,
			Long groupId) throws Exception;

	String changeNodeMetadataEpm(MimeType mimeType, UUID nodeId, String xmlMetadataEpm, Long id, long groupId)
			throws Exception;

	String changeNodeMetadata(MimeType mimeType, UUID nodeId, String xmlNode, Long id, long groupId)
			throws DoesNotExistException, BusinessException, Exception;

	String changeNodeContext(MimeType mimeType, UUID nodeId, String xmlNode, Long userId, Long groupId)
			throws BusinessException, Exception;

	String changeNodeResource(MimeType mimeType, UUID nodeId, String xmlNode, Long id, Long groupId)
			throws BusinessException, Exception;

	String addNode(MimeType inMimeType, UUID parentNodeId, String xmlNode, Long userId, Long groupId,
			boolean forcedUuid) throws Exception;

	String getNodeWithXSL(MimeType textXml, UUID nodeId, String xslFile, String parameters, Long id, Long groupId)
			throws BusinessException, Exception;

	String getNodes(MimeType mimeType, String rootNodeCode, String childSemtag, Long userId, Long groupId,
			String parentSemtag, String parentNodeCode, Integer cutoff) throws BusinessException;

	String executeAction(Long userId, UUID nodeId, String action, String role);

	String copyNode(MimeType inMimeType, UUID destId, String tag, String code, UUID sourceId, Long userId,
			Long groupId) throws Exception;

	UUID importNode(MimeType mimeType, UUID parentId, String semtag, String code, UUID sourceId, Long id,
			long groupId) throws BusinessException, Exception;

	int updateNodeCode(UUID nodeId, String code);

	UUID getChildUuidBySemtag(UUID rootId, String semantictag);

	List<Node> getChildren(UUID nodeId);
}
