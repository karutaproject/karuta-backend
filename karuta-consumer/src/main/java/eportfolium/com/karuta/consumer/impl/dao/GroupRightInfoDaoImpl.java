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

package eportfolium.com.karuta.consumer.impl.dao;
// Generated 17 juin 2019 11:33:18 by Hibernate Tools 5.2.10.Final

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import eportfolium.com.karuta.consumer.contract.dao.GroupInfoDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupRightInfoDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupRightsDao;
import eportfolium.com.karuta.consumer.contract.dao.GroupUserDao;
import eportfolium.com.karuta.model.bean.GroupRightInfo;
import eportfolium.com.karuta.model.bean.GroupRights;
import eportfolium.com.karuta.model.bean.GroupUser;
import eportfolium.com.karuta.model.bean.Portfolio;
import eportfolium.com.karuta.util.PhpUtil;

/**
 * Home object implementation for domain model class GroupRightInfo.
 * 
 * @see dao.GroupRightInfo
 * @author Hibernate Tools
 */
@Repository
public class GroupRightInfoDaoImpl extends AbstractDaoImpl<GroupRightInfo> implements GroupRightInfoDao {

	@Autowired
	private GroupInfoDao groupInfoDao;

	@Autowired
	private GroupUserDao groupUserDao;

	@Autowired
	private GroupRightsDao groupRightsDao;

	public GroupRightInfoDaoImpl() {
		super();
		setCls(GroupRightInfo.class);
	}

	/**
	 * Vérifie si le role existe pour ce portfolio
	 *
	 */
	public GroupRightInfo getByPortfolioAndLabel(String portfolioUuid, String label) {
		return getByPortfolioAndLabel(UUID.fromString(portfolioUuid), label);
	}

	/**
	 * Vérifie si le role existe pour ce portfolio
	 *
	 */
	public GroupRightInfo getByPortfolioAndLabel(UUID portfolioUuid, String label) {
		GroupRightInfo gri = null;
		String sql = "SELECT gri FROM GroupRightInfo gri";
		sql += " INNER JOIN FETCH gri.portfolio p";
		sql += " WHERE p.id = :portfolioUuid";
		sql += " AND gri.label = :label";
		TypedQuery<GroupRightInfo> q = em.createQuery(sql, GroupRightInfo.class);
		q.setParameter("portfolioUuid", portfolioUuid);
		q.setParameter("label", label);
		try {
			gri = q.getSingleResult();
		} catch (NoResultException e) {
		}
		return gri;
	}

	/**
	 * S'assure qu'il y ait au moins un groupe de base
	 * 
	 * @param portfolioUuid
	 * @return
	 */
	public List<GroupRightInfo> getDefaultByPortfolio(UUID portfolioUuid) {
		String sql = "SELECT gri FROM GroupRightInfo gri";
		sql += " LEFT JOIN gri.groupInfo gi";
		sql += " WHERE gri.portfolio.id = :portfolioUuid";
		sql += " AND gri.label='all'";
		TypedQuery<GroupRightInfo> q = em.createQuery(sql, GroupRightInfo.class);
		q.setParameter("portfolioUuid", portfolioUuid);
		return q.getResultList();
	}

	public List<GroupRightInfo> getDefaultByPortfolio(String portfolioUuid) {
		return getDefaultByPortfolio(UUID.fromString(portfolioUuid));
	}

	/**
	 * Les rôles reliés à un portfolio
	 */
	public List<GroupRightInfo> getByPortfolioID(String portfolioUuid) {
		return getByPortfolioID(UUID.fromString(portfolioUuid));
	}

	/**
	 * Les rôles reliés à un portfolio
	 */
	public List<GroupRightInfo> getByPortfolioID(UUID portfolioUuid) {
		String sql = "SELECT gri FROM GroupRightInfo gri";
		sql += " LEFT JOIN FETCH gri.groupInfo gi";
		sql += " LEFT JOIN FETCH gri.groupRights gr";
		sql += " LEFT JOIN FETCH gi.groupUser gu";
		sql += " WHERE gri.portfolio.id = :portfolioUuid";
		TypedQuery<GroupRightInfo> q = em.createQuery(sql, GroupRightInfo.class);
		q.setParameter("portfolioUuid", portfolioUuid);
		return q.getResultList();
	}

	public boolean groupRightInfoExists(Long grid) {
		boolean result = false;
		String sql = "SELECT gri FROM GroupRightInfo gri";
		sql += " WHERE gri.id = :grid";
		Query q = em.createQuery(sql);
		q.setParameter("grid", grid);
		try {
			q.getSingleResult();
			result = true;
		} catch (Exception e) {
		}
		return result;
	}

	@Override
	public Long add(String portfolioUuid, String role) {
		Long result = 0L;
		GroupRightInfo gri = new GroupRightInfo();
		gri.setOwner(1);
		gri.setLabel(role);
		gri.setPortfolio(new Portfolio(UUID.fromString(portfolioUuid)));
		try {
			persist(gri);
			result = gri.getId();
		} catch (Exception e) {
		}
		return result;
	}

	@Override
	public GroupRightInfo add(Portfolio portfolio, String role) {
		GroupRightInfo result = null;
		GroupRightInfo gri = new GroupRightInfo();
		gri.setOwner(1);
		gri.setLabel(role);
		gri.setPortfolio(portfolio);
		try {
			persist(gri);
			result = gri;
		} catch (Exception e) {
			// TODO: handle exception
		}
		return result;
	}

	/**
	 * Check if role already exists
	 */
	public Long getIdByNodeAndLabel(String nodeUuid, String label) {
		Long res = null;
		String sql = "SELECT gri.id FROM GroupRightInfo gri, Node n";
		sql += " INNER JOIN gri.portfolio p1";
		sql += " INNER JOIN n.portfolio p2";
		sql += " INNER JOIN gri.groupInfo gi";
		sql += " WHERE p1.id = p2.id";
		sql += " AND n.id = :nodeUuid";
		sql += " AND gri.label = :label";

		TypedQuery<Long> q = em.createQuery(sql, Long.class);
		q.setParameter("nodeUuid", UUID.fromString(nodeUuid));
		q.setParameter("label", label);
		try {
			res = q.getSingleResult();
		} catch (NoResultException e) {
		}
		return res;
	}

	/**
	 * Obtenir les roles reliés à un noeud
	 * 
	 * @param nodeUuid
	 * @return
	 */
	public List<GroupRightInfo> getByNode(String nodeUuid) {
		return getByNode(UUID.fromString(nodeUuid));
	}

	/**
	 * Obtenir les roles reliés à un noeud
	 * 
	 * @param nodeUuid
	 * @return
	 */
	public List<GroupRightInfo> getByNode(UUID nodeUuid) {
		String sql = "SELECT gri FROM GroupRightInfo gri, Node n";
		sql += " INNER JOIN gri.portfolio p1";
		sql += " INNER JOIN n.portfolio p2";
		sql += " INNER JOIN gri.groupInfo gi";
		sql += " WHERE p1.id = p2.id";
		sql += " AND n.id = :nodeUuid";
		TypedQuery<GroupRightInfo> q = em.createQuery(sql, GroupRightInfo.class);
		q.setParameter("nodeUuid", nodeUuid);
		return q.getResultList();
	}

	/**
	 * Role et uuid
	 * 
	 * @param uuid
	 * @param label
	 * @return
	 */
	public List<Long> getByNodeAndLabel(UUID nodeUuid, List<String> labels) {
		if (CollectionUtils.isNotEmpty(labels)) {
			for (int i = 0; i < labels.size(); i++) {
				labels.set(i, StringUtils.prependIfMissing(labels.get(i), "'", "\""));
				labels.set(i, StringUtils.appendIfMissing(labels.get(i), "'", "\""));
			}
		}
		String sql = "SELECT gri.id FROM GroupRightInfo gri, Node n";
		sql += " INNER JOIN gri.portfolio p1";
		sql += " INNER JOIN n.portfolio p2";
		sql += " INNER JOIN gri.groupInfo gi";
		sql += " WHERE p1.id = p2.id";
		sql += " AND n.id = :nodeUuid";
		sql += " AND gri.label IN (" + PhpUtil.implode(",", labels) + ")";

		final TypedQuery<Long> q = em.createQuery(sql, Long.class);
		q.setParameter("nodeUuid", nodeUuid);
		return q.getResultList();
	}

	public List<Long> getByNodeAndLabel(String nodeUuid, List<String> labels) {
		return getByNodeAndLabel(UUID.fromString(nodeUuid), labels);
	}

	@Override
	public boolean isOwner(Long userId, Long rrgId) {
		if (userId == null)
			return false;

		String query = "SELECT gri FROM GroupRightInfo gri";
		query += " INNER JOIN gri.portfolio p WITH p.modifUserId = :userId";
		query += " WHERE gri.id = :grid";
		TypedQuery<GroupRightInfo> q = em.createQuery(query, GroupRightInfo.class);
		q.setParameter("userId", userId);
		q.setParameter("grid", rrgId);
		try {
			q.getSingleResult();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void removeById(Long groupRightInfoId) throws Exception {
		String sql = "SELECT gri FROM GroupRightInfo gri";
		sql += " LEFT JOIN FETCH gri.groupRights gr";
		sql += " LEFT JOIN FETCH gri.groupInfo gi";
		sql += " LEFT JOIN FETCH gi.groupUser gu";
		sql += " WHERE gri.id = :grid";

		TypedQuery<GroupRightInfo> query = em.createQuery(sql, GroupRightInfo.class);
		query.setParameter("grid", groupRightInfoId);
		GroupRightInfo gri = query.getSingleResult();
		if (CollectionUtils.isNotEmpty(gri.getGroupRights())) {
			for (Iterator<GroupRights> it = gri.getGroupRights().iterator(); it.hasNext();) {
				groupRightsDao.remove(it.next());
				it.remove();
			}
		}
		if (gri.getGroupInfo() != null && CollectionUtils.isNotEmpty(gri.getGroupInfo().getGroupUser())) {
			for (Iterator<GroupUser> it = gri.getGroupInfo().getGroupUser().iterator(); it.hasNext();) {
				groupUserDao.remove(it.next());
				it.remove();
			}
		}
		if (gri.getGroupInfo() != null) {
			groupInfoDao.remove(gri.getGroupInfo());
		}
		remove(gri);
	}

	public List<GroupRightInfo> getByPortfolioAndUser(String portfolioUuid, Long userId) {
		return getByPortfolioAndUser(UUID.fromString(portfolioUuid), userId);
	}

	public List<GroupRightInfo> getByPortfolioAndUser(UUID portfolioUuid, Long userId) {
		String sql = "SELECT DISTINCT gri FROM ResourceTable r, GroupRights gr";
		sql += " INNER JOIN gr.id.groupRightInfo gri WITH gri.portfolio.id = :portfolioUuid";
		sql += " WHERE r.credential.id = :userId";
		sql += " AND gr.id.id = r.id";
		TypedQuery<GroupRightInfo> query = em.createQuery(sql, GroupRightInfo.class);
		query.setParameter("userId", userId);
		query.setParameter("portfolioUuid", portfolioUuid);
		return query.getResultList();
	}

	public List<GroupRightInfo> getByUser(Long userId) {
		String sql = "SELECT DISTINCT gri FROM ResourceTable r, GroupRights gr";
		sql += " INNER JOIN gr.id.groupRightInfo gri";
		sql += " WHERE r.credential.id = :userId";
		sql += " AND gr.id.id = r.id";
		TypedQuery<GroupRightInfo> query = em.createQuery(sql, GroupRightInfo.class);
		query.setParameter("userId", userId);
		return query.getResultList();
	}

	/// Fetch roles to be notified
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> getRolesToBeNotified(Long groupId, Long userId, String uuid) {
		Map<String, Object> res = null;
		String sql = "SELECT new map(portfolio.id as portfolioUUID, gr.notifyRoles AS notifyRoles) FROM GroupUser gu";
		sql += " INNER JOIN gu.id.groupInfo gi WITH gi.id = :groupId ";
		sql += " INNER JOIN gi.groupRightInfo gri";
		sql += " INNER JOIN gri.portfolio portfolio";
		sql += " INNER JOIN gri.groupRights gr WITH gr.id.id = :uuid";
		sql += " WHERE gu.id.credential.id = :userId";
		try {
			Query query = em.createQuery(sql);
			query.setParameter("groupId", groupId);
			query.setParameter("uuid", UUID.fromString(uuid));
			query.setParameter("userId", userId);
			res = (Map<String, Object>) query.getSingleResult();
		} catch (NoResultException e) {
		}
		return res;
	}

	@Override
	public ResultSet getMysqlGroupRightsInfos(Connection con) throws SQLException {
		PreparedStatement st;
		String sql;

		try {
			// On récupère d'abord les informations dans la table structures
			sql = "SELECT grid, owner, label, bin2uuid(portfolio_id) as portfolio_id, change_rights FROM group_right_info";
			st = con.prepareStatement(sql);
			return st.executeQuery();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}
