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

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.springframework.stereotype.Repository;

import eportfolium.com.karuta.consumer.contract.dao.GroupUserDao;
import eportfolium.com.karuta.model.bean.Credential;
import eportfolium.com.karuta.model.bean.GroupInfo;
import eportfolium.com.karuta.model.bean.GroupUser;
import eportfolium.com.karuta.model.bean.GroupUserId;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.util.PhpUtil;
import eportfolium.com.karuta.util.Tools;

/**
 * Home object implementation for domain model class GroupUser.
 * 
 * @see dao.GroupUser
 * @author Hibernate Tools
 */
@Repository
public class GroupUserDaoImpl extends AbstractDaoImpl<GroupUser> implements GroupUserDao {

	public GroupUserDaoImpl() {
		super();
		setCls(GroupUser.class);
	}

	public List<GroupUser> getByUser(final Long userId) {
		if (PhpUtil.empty(userId)) {
			throw new IllegalArgumentException(Tools.displayError("userId invalide"));
		}

		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.credential cr";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE cr.id = :userId ";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("userId", userId);
		return q.getResultList();
	}

	public boolean isUserInGroup(final Long userId, final Long groupId) {
		String sql;
		boolean retval = false;

		sql = "SELECT gu FROM GroupUser gu";
		sql += " WHERE gu.id.credential.id = :userID";
		sql += " AND gu.id.groupInfo.id = :groupID";
		Query q = em.createQuery(sql);
		q.setParameter("userID", userId);
		q.setParameter("groupID", groupId);
		try {
			q.getSingleResult();
			retval = true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return retval;
	}

	public boolean isUserMemberOfGroup(Long userId, Long groupId) {
		boolean status = false;
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " WHERE gu.id.credential.id = :userId";
		sql += " AND gu.id.groupInfo.id = :groudId";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("userId", userId);
		q.setParameter("groudId", groupId);
		try {
			q.getSingleResult();
			status = true;
		} catch (NoResultException e) {
			e.printStackTrace();
		}
		return status;
	}

	public List<GroupUser> getByPortfolio(String portfolioUuid) {
		return getByPortfolio(UUID.fromString(portfolioUuid));
	}

	public List<GroupUser> getByPortfolio(UUID portfolioUuid) {
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE gri.portfolio.id = :portfolioUuid";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("portfolioUuid", portfolioUuid);
		return q.getResultList();
	}

	public List<GroupUser> getByPortfolioAndUser(String portfolioUuid, Long userId) {
		return getByPortfolioAndUser(UUID.fromString(portfolioUuid), userId);
	}

	public List<GroupUser> getByPortfolioAndUser(UUID portfolioUuid, Long userId) {
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.credential cr";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE gri.portfolio.id = :portfolioUuid";
		sql += " AND cr.id = :userId";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("portfolioUuid", portfolioUuid);
		q.setParameter("userId", userId);
		return q.getResultList();
	}

	/**
	 * Ajoute la personne dans ce groupe
	 */
	public Integer addUserInGroup(String usergroupId, String userId) {
		Integer retval = Integer.valueOf(0);
		try {
			final Long gid = Long.valueOf(usergroupId);
			final Long uid = Long.valueOf(userId);
			final GroupUser gu = new GroupUser(new GroupUserId(new GroupInfo(gid), new Credential(uid)));
			persist(gu);
		} catch (Exception ex) {
			ex.printStackTrace();
			retval = Integer.valueOf(1);
		}
		return retval;
	}

	/**
	 * Ajoute la personne dans ce groupe
	 */
	public Long addUserInGroup(Credential userId, GroupInfo groupid) {
		Long retval = Long.valueOf(0);
		try {
			final GroupUser gu = new GroupUser(new GroupUserId(groupid, userId));
			persist(gu);
		} catch (Exception ex) {
			ex.printStackTrace();
			retval = Long.valueOf(1);
		}
		return retval;
	}

	@Override
	public void removeByUserAndRole(Long userId, Long rrgId) throws BusinessException {
		remove(getByUserAndRole(userId, rrgId));
	}

	public void removeByPortfolio(String portId) throws Exception {
		deleteByPortfolio2(UUID.fromString(portId));
	}

	public void deleteByPortfolio2(UUID portId) throws Exception {
		String sql = "SELECT gi.id FROM GroupRightInfo gri";
		sql += " INNER JOIN gri.groupInfo gi";
		sql += " WHERE gri.portfolio.id = :portfolioUuid";
		TypedQuery<Long> q1 = em.createQuery(sql, Long.class);
		q1.setParameter("portfolioUuid", portId);
		List<Long> gidList = q1.getResultList();

		sql = "SELECT gu FROM GroupUser gu";
		sql += " WHERE gu.id.groupInfo.id IN (" + PhpUtil.implode(",", gidList) + ")";

		TypedQuery<GroupUser> q2 = em.createQuery(sql, GroupUser.class);
		List<GroupUser> guList = q2.getResultList();

		for (Iterator<GroupUser> it = guList.iterator(); it.hasNext();) {
			remove(it.next());
			it.remove();
		}
	}

	public int deleteByPortfolio(UUID portId) {
		int result = 0;
		List<GroupUser> l = getByPortfolio(portId);
		Iterator<GroupUser> it = l.iterator();
		try {
			while (it.hasNext()) {
				remove(it.next());
			}
		} catch (Exception e) {
			result = 1;
		}
		return result;
	}

	public int deleteByPortfolio(String portId) {
		return deleteByPortfolio(UUID.fromString(portId));
	}

	public GroupUser getByUserAndRole(Long userId, Long rrgid) {
		GroupUser res = null;
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.credential cr";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE cr.id = :userId";
		sql += " AND gri.id = :grid";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("userId", userId);
		q.setParameter("grid", rrgid);
		try {
			res = q.getSingleResult();
		} catch (NoResultException e) {
		}
		return res;
	}

	@Override
	public List<GroupUser> getByRole(Long rrgid) {
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.credential cr";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE gri.id = :grid";
		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("grid", rrgid);
		List<GroupUser> resList = q.getResultList();
		return resList;
	}

	@Override
	public GroupUser getUniqueByUser(Long userId) {
		if (PhpUtil.empty(userId)) {
			throw new IllegalArgumentException(Tools.displayError("userId invalide"));
		}

		GroupUser gu = null;
		String sql = "SELECT gu FROM GroupUser gu";
		sql += " INNER JOIN FETCH gu.id.credential cr";
		sql += " INNER JOIN FETCH gu.id.groupInfo gi";
		sql += " INNER JOIN FETCH gi.groupRightInfo gri";
		sql += " WHERE cr.id = :userId";
		sql += " AND cr.login = gri.label";

		TypedQuery<GroupUser> q = em.createQuery(sql, GroupUser.class);
		q.setParameter("userId", userId);
		try {
			gu = q.getSingleResult();
		} catch (Exception e) {
		}
		return gu;
	}

}
