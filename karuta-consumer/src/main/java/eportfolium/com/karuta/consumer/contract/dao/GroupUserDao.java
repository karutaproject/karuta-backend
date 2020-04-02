package eportfolium.com.karuta.consumer.contract.dao;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import eportfolium.com.karuta.model.bean.Credential;
import eportfolium.com.karuta.model.bean.GroupInfo;
import eportfolium.com.karuta.model.bean.GroupUser;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.model.exception.DoesNotExistException;

public interface GroupUserDao {

	void persist(GroupUser transientInstance);

	void remove(GroupUser persistentInstance);

	GroupUser merge(GroupUser detachedInstance);

	GroupUser findById(Serializable id) throws DoesNotExistException;

	void removeById(final Serializable id) throws DoesNotExistException;

	List<GroupUser> findAll();

	List<GroupUser> getByUser(final Long userId);

	boolean isUserInGroup(Long userId, Long groupId);

	boolean isUserMemberOfGroup(Long userId, Long groupId);

	List<GroupUser> getByPortfolio(String portfolioUuid);

	List<GroupUser> getByPortfolio(UUID portfolioUuid);

	List<GroupUser> getByPortfolioAndUser(String portfolioUuid, Long userId);

	List<GroupUser> getByPortfolioAndUser(UUID portfolioUuid, Long userId);

	/**
	 * Ajoute la personne dans ce groupe
	 * 
	 * @param userGroupId
	 * @param userId
	 * @return
	 */
	Integer addUserInGroup(String userGroupId, String userId);

	/**
	 * Supprime les utilisateurs des RRG d'un portfolio donné
	 * 
	 * @param portId
	 * @return
	 */
	int deleteByPortfolio(String portId);

	int deleteByPortfolio(UUID portId);

	/**
	 * Ajoute la personne dans ce groupe
	 * 
	 * @param userId
	 * @param groupid
	 * 
	 * @return
	 */
	Long addUserInGroup(Credential userId, GroupInfo groupid);

	GroupUser getByUserAndRole(Long userId, Long rrgid);

	List<GroupUser> getByRole(Long rrgid);

	void removeByUserAndRole(Long userId, Long rrgId) throws BusinessException;

	void removeByPortfolio(String portId) throws Exception;

	void deleteByPortfolio2(UUID portId) throws Exception;

	GroupUser getUniqueByUser(Long userId) throws Exception;

	ResultSet findAll(String table, Connection con);

	void removeAll();

}