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

import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import eportfolium.com.karuta.business.contract.ConfigurationManager;
import eportfolium.com.karuta.consumer.repositories.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.validator.routines.EmailValidator;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.PasswordGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import eportfolium.com.karuta.business.contract.EmailManager;
import eportfolium.com.karuta.business.contract.SecurityManager;
import eportfolium.com.karuta.consumer.util.DomUtils;
import eportfolium.com.karuta.model.bean.Credential;
import eportfolium.com.karuta.model.bean.CredentialGroup;
import eportfolium.com.karuta.model.bean.CredentialGroupMembers;
import eportfolium.com.karuta.model.bean.CredentialGroupMembersId;
import eportfolium.com.karuta.model.bean.CredentialSubstitution;
import eportfolium.com.karuta.model.bean.CredentialSubstitutionId;
import eportfolium.com.karuta.model.bean.GroupInfo;
import eportfolium.com.karuta.model.bean.GroupRightInfo;
import eportfolium.com.karuta.model.bean.GroupUser;
import eportfolium.com.karuta.model.bean.GroupUserId;
import eportfolium.com.karuta.model.bean.Node;
import eportfolium.com.karuta.model.bean.Portfolio;
import eportfolium.com.karuta.model.exception.AuthenticationException;
import eportfolium.com.karuta.model.exception.BusinessException;
import eportfolium.com.karuta.model.exception.GenericBusinessException;
import eportfolium.com.karuta.model.exception.ValueRequiredException;

/**
 * Cette classe rassemble la gestion et la modification des utilisateurs, des
 * groupes et des rôles. Le cycle de vie entier de l’utilisateur, de la création
 * à la suppression de son identité au sein du système, est alors contrôlé en un
 * seul endroit.
 * 
 * @author mlengagne
 *
 */
@Service
@Transactional
public class SecurityManagerImpl implements SecurityManager {

	@Autowired
	private EmailManager emailManager;

	@Autowired
	private CredentialRepository credentialRepository;

	@Autowired
	private CredentialSubstitutionRepository credentialSubstitutionRepository;

	@Autowired
	private CredentialGroupMembersRepository credentialGroupMembersRepository;

	@Autowired
	private GroupUserRepository groupUserRepository;

	@Autowired
	private GroupRightInfoRepository groupRightInfoRepository;

	@Autowired
	private GroupInfoRepository groupInfoRepository;

	@Autowired
	private PortfolioRepository portfolioRepository;

	@Autowired
	private ConfigurationManager configurationManager;

	/**
	 * Each token produced by this class uses this identifier as a prefix.
	 */
	public static final String ID = "$31$";

	/**
	 * The minimum recommended cost, used by default
	 */
	public static final int DEFAULT_COST = 16;

	private static final String ALGORITHM = "PBKDF2WithHmacSHA512";

	/**
	 * A keyLength of 256 would be safer :).
	 */
	private static final int SIZE = 128;

	private static final Pattern layout = Pattern.compile("\\$31\\$(\\d\\d?)\\$(.{43})");

	private final SecureRandom random;

	private final int cost;

	private static final Log log = LogFactory.getLog(SecurityManagerImpl.class);

	public SecurityManagerImpl() {
		this(DEFAULT_COST);
	}

	/**
	 * Create a SecurityManager with a specified cost
	 * 
	 * @param cost the exponential computational cost of hashing a password, 0 to 30
	 */
	private SecurityManagerImpl(int cost) {
		iterations(cost); /* Validate cost */
		this.cost = cost;
		this.random = new SecureRandom();
	}

	/**
	 * The token generated is stored at the server, and should be associated with
	 * the user identity. For example, a user table with id, login name and/or email
	 * address, and token. When someone logs in with login and password, lookup the
	 * stored token with the login and pass that to the authenticate() method with
	 * the password.
	 */
	public boolean changePassword(String username, String password) {
		try {
			Credential credential = credentialRepository.findByLogin(username);
			setPassword(password, credential);

			credentialRepository.save(credential);

			return true;
		} catch (BusinessException e) {
			return false;
		}
	}

	/**
	 * This method provides a way for users to change their own userPassword.
	 * 
	 * @param userId
	 * @param currentPassword
	 * @param newPassword
	 * @throws BusinessException
	 */
	public void changeUserPassword(Long userId, String currentPassword, String newPassword) throws BusinessException {
		Credential user = credentialRepository
							.findById(userId)
							.orElse(new Credential());

		if (!authenticate(currentPassword.toCharArray(), user.getPassword())) {
			throw new AuthenticationException("User_password_incorrect");
		}

		if (user.getPassword() != null && authenticate(newPassword.toCharArray(), user.getPassword())) {
			throw new GenericBusinessException("User_newpassword_is_same");
		}

		setPassword(newPassword, user);
		credentialRepository.save(user);
	}

	public boolean registerUser(String username, String password) {
		if (!credentialRepository.existsByLogin(username)) {
			Credential newUser = new Credential();
			newUser.setLogin(username);

			try {
				setPassword(password, newUser);

				newUser.setDisplayFirstname("");
				newUser.setDisplayLastname("");
				newUser.setOther("");
				newUser.setIsDesigner(Integer.valueOf(1));

				credentialRepository.save(newUser);

				return true;
			} catch (BusinessException e) {
				e.printStackTrace();

				return false;
			}
		}

		return false;
	}

	public Long addUser(String username, String email) throws BusinessException {

		if (!EmailValidator.getInstance().isValid(email)) {
			throw new IllegalArgumentException();
		}

		final Credential cr = new Credential();

		cr.setLogin(username);
		cr.setDisplayFirstname("");
		cr.setDisplayLastname("");
		cr.setEmail(email);
		cr.setOther("");
		cr.setActive(1);

		setPassword(generatePassword(), cr);

		credentialRepository.save(cr);

		return cr.getId();
	}

	public String generatePassword() {
		List<CharacterRule> rules = Arrays.asList(
				// at least one upper-case character
				new CharacterRule(EnglishCharacterData.UpperCase, 1),

				// at least one lower-case character
				new CharacterRule(EnglishCharacterData.LowerCase, 1),

				// at least one digit character
				new CharacterRule(EnglishCharacterData.Digit, 1),

				// at least one symbol (special character)
				new CharacterRule(EnglishCharacterData.Special, 1));

		PasswordGenerator generator = new PasswordGenerator();

		// Generated password is 12 characters long, which complies with policy
		String password = generator.generatePassword(12, rules);
		return password;
	}

	/**
	 * Do all tests of userPassword size, content, history, etc. here...
	 * 
	 * @param newPassword
	 * @param credential
	 */
	private void setPassword(String newPassword, Credential credential) throws BusinessException {
		if (StringUtils.isEmpty(newPassword)) {
			throw new ValueRequiredException(credential, "User_newpassword_is_required");
		}

		credential.setPassword(hash(newPassword.toCharArray()));
	}

	private static int iterations(int cost) {
		if ((cost < 0) || (cost > 31))
			throw new IllegalArgumentException("cost: " + cost);
		return 1 << cost;
	}

	/**
	 * Hash a password for storage. *
	 * 
	 * <p>
	 * Passwords should be stored in a {@code char[]} so that it can be filled with
	 * zeros after use instead of lingering on the heap and elsewhere.
	 * 
	 * @return a secure authentication token to be stored for later authentication
	 */
	private String hash(char[] password) {
		byte[] salt = new byte[SIZE / 8];
		random.nextBytes(salt);
		byte[] dk = pbkdf2(password, salt, 1 << cost);
		byte[] hash = new byte[salt.length + dk.length];
		System.arraycopy(salt, 0, hash, 0, salt.length);
		System.arraycopy(dk, 0, hash, salt.length, dk.length);
		Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
		return ID + cost + '$' + enc.encodeToString(hash);
	}

	/**
	 * Authenticate with a password and a stored password token.
	 * 
	 * @return true if the password and token match
	 */
	private boolean authenticate(char[] password, String token) {
		Matcher m = layout.matcher(token);
		if (!m.matches())
			throw new IllegalArgumentException("Invalid token format");
		int iterations = iterations(Integer.parseInt(m.group(1)));
		byte[] hash = Base64.getUrlDecoder().decode(m.group(2));
		byte[] salt = Arrays.copyOfRange(hash, 0, SIZE / 8);
		byte[] check = pbkdf2(password, salt, iterations);
		int zero = 0;
		for (int idx = 0; idx < check.length; ++idx)
			zero |= hash[salt.length + idx] ^ check[idx];
		return zero == 0;
	}

	private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
		KeySpec spec = new PBEKeySpec(password, salt, iterations, SIZE);
		try {
			SecretKeyFactory f = SecretKeyFactory.getInstance(ALGORITHM);
			return f.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Missing algorithm: " + ALGORITHM, ex);
		} catch (InvalidKeySpecException ex) {
			throw new IllegalStateException("Invalid SecretKeyFactory", ex);
		}
	}

	@Override
	public void removeUser(Long byUser, Long forUser) throws BusinessException {
		if (!credentialRepository.isAdmin(byUser))
			throw new GenericBusinessException("FORBIDDEN : No admin right");

		credentialRepository.deleteById(forUser);
	}

	public void removeUsers(Long byUser, Long forUser) throws BusinessException {
		if (!credentialRepository.isAdmin(byUser) && byUser != forUser)
			throw new GenericBusinessException("FORBIDDEN : No admin right");

		groupUserRepository.deleteAll(groupUserRepository.getByUser(forUser));
		credentialRepository.deleteById(forUser);
	}

	public String addUsers(String xmlUsers, Long userId) throws Exception {
		if (!credentialRepository.isAdmin(userId) && !credentialRepository.isCreator(userId))
			throw new GenericBusinessException("403 FORBIDDEN : No admin right");

		String result = null;
		Credential cr = null;

		Long id = 0L;
		NodeList children2 = null;
		org.w3c.dom.Node currentItem = null;
		String nodeName = null;

		// On récupère le body
		Document doc = DomUtils.xmlString2Document(xmlUsers, new StringBuffer());
		Element users = doc.getDocumentElement();

		NodeList children = users.getChildNodes();
		// On parcourt une première fois les enfants pour récupérer la liste et écrire
		// en base

		// On vérifie le bon format
		StringBuilder userdone = new StringBuilder();
		userdone.append("<users>");
		String value = null;
		try {
			if (users.getNodeName().equals("users")) {
				for (int i = 0; i < children.getLength(); i++) {
					if (children.item(i).getNodeName().equals("user")) {
						cr = new Credential();
						cr.setActive(1);
						cr.setOther("");
						cr.setEmail("");

						children2 = children.item(i).getChildNodes();

						for (int j = 0; j < children2.getLength(); j++) {
							currentItem = children2.item(j);
							nodeName = currentItem.getNodeName();
							value = DomUtils.getInnerXml(currentItem);

							if (nodeName.equals("username")) {
								cr.setLogin(value);
							} else if (nodeName.equals("password")) {
								setPassword(value, cr);
							} else if (nodeName.equals("firstname")) {
								cr.setDisplayFirstname(StringUtils.defaultString(value));
							} else if (nodeName.equals("lastname")) {
								cr.setDisplayLastname(StringUtils.defaultString(value));
							} else if (nodeName.equals("email")) {
								cr.setEmail(StringUtils.defaultString(value));
							} else if (nodeName.equals("active")) {
								if ("1".equals(value))
									cr.setActive(1);
								else
									cr.setActive(0);
							} else if (nodeName.equals("designer")) {
								if ("1".equals(value))
									cr.setIsDesigner(1);
								else
									cr.setIsDesigner(0);
							} else if (nodeName.equals("substitute")) {
								if ("1".equals(value))
									cr.setCanSubstitute(1);
								else
									cr.setCanSubstitute(0);
							} else if (nodeName.equals("other")) {
								cr.setOther(value);
							}
						}

						// On ajoute l'utilisateur dans la base de données
						credentialRepository.save(cr);
						id = cr.getId();

						CredentialSubstitution subst = new CredentialSubstitution();
						/// FIXME: More complete rule to use
						CredentialSubstitutionId csId = new CredentialSubstitutionId();
						// id = 0, ne pas vérifier qui cette personne peut remplacer (sauf root)
						csId.setId(0L);
						csId.setCredential(cr);
						csId.setType("USER");

						if (cr.getCanSubstitute() == 1) {
							subst.setId(csId);
							credentialSubstitutionRepository.save(subst);
							cr.setCredentialSubstitution(subst);
						} else {
							credentialSubstitutionRepository.deleteById(csId);
						}

						userdone.append("<user ").append("id=\"").append(id).append("\">");
						userdone.append("<username>").append(cr.getLogin()).append("</username>");
						userdone.append("<firstname>").append(cr.getDisplayFirstname()).append("</firstname>");
						userdone.append("<lastname>").append(cr.getDisplayLastname()).append("</lastname>");
						userdone.append("<email>").append(cr.getEmail()).append("</email>");
						userdone.append("<active>").append(cr.getActive()).append("</active>");
						userdone.append("<designer>").append(cr.getIsDesigner()).append("</designer>");
						userdone.append("<substitute>").append(cr.getSubUser()).append("</substitute>");
						userdone.append("<other>").append(cr.getOther()).append("</other>");
						userdone.append("</user>");
					}
				}
			} else {
				result = "Missing 'users' tag";
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			result = "Error when processing user : " + cr != null ? cr.getLogin() : "undefined";
		}
		userdone.append("</users>");

		if (result == null)
			result = userdone.toString();

		return result;
	}

	public boolean addUser(String username, String email, boolean isDesigner, long userId) throws Exception {
		if (!credentialRepository.isAdmin(userId) && !credentialRepository.isCreator(userId))
			throw new GenericBusinessException("Status.FORBIDDEN : No admin right");

		if (!credentialRepository.existsByLogin(username)) {

			try {
				Credential newUser = new Credential();
				String passwd = generatePassword();
				/// Credential checking use hashing, we'll never reach this.
				setPassword(passwd, newUser);
				newUser.setLogin(username);
				newUser.setEmail(email);
				newUser.setActive(1);
				newUser.setDisplayFirstname("");
				newUser.setOther("");
				newUser.setDisplayLastname("");
				newUser.setIsDesigner(BooleanUtils.toInteger(isDesigner));

				credentialRepository.save(newUser);


				final Map<String, String> template_vars = new HashMap<String, String>();
				template_vars.put("firstname", username);
				template_vars.put("lastname", "");
				template_vars.put("email", email);
				template_vars.put("passwd", passwd);

				final Integer langId = Integer.valueOf(configurationManager.get("PS_LANG_DEFAULT"));
				try {
					// Envoie d'un e-mail à l'utilisateur
					emailManager.send(langId, "account", emailManager.getTranslation("Welcome!"), template_vars, email,
							username);
				} catch (Exception e) {
					e.printStackTrace();
				}

				return true;
			} catch (BusinessException e) {
				e.printStackTrace();

				return false;
			}
		}

		return false;
	}

	public String changeUser(Long byUserId, Long forUserId, String xmlUser) throws BusinessException {
		if (credentialRepository.existsById(forUserId)) {
			throw new GenericBusinessException("Unknown user id");
		}

		String result1 = null;
		String currentPassword = null;
		String newPassword = null;
		String email = null;
		String userlogin = null;
		String firstname = null;
		String lastname = null;
		String active = null;
		String is_admin = null;
		String is_designer = null;
		String hasSubstitute = null;
		String other = "";

		// On récupère le body
		Document doc;
		Element infUser = null;
		try {
			doc = DomUtils.xmlString2Document(xmlUser, new StringBuffer());
			infUser = doc.getDocumentElement();
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (infUser.getNodeName().equals("user")) {
			// On récupère les attributs
			NodeList children = infUser.getChildNodes();
			/// Fetch parameters
			/// TODO Make some function out of this I think
			for (int y = 0; y < children.getLength(); y++) {
				if (children.item(y).getNodeName().equals("username")) {
					userlogin = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("prevpass")) {
					currentPassword = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("password")) {
					newPassword = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("firstname")) {
					firstname = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("lastname")) {
					lastname = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("email")) {
					email = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("admin")) {
					is_admin = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("designer")) {
					is_designer = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("active")) {
					active = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("substitute")) {
					hasSubstitute = DomUtils.getInnerXml(children.item(y));
				}
				if (children.item(y).getNodeName().equals("other")) {
					other = DomUtils.getInnerXml(children.item(y));
				}
			}

			/// Vérifier si l'utilisateur a le bon mot de passe afin d'exécuter les
			/// modifications
			boolean isOK = checkPassword(byUserId, currentPassword);

			/// Vérifier si l'utilisateur qui demande les modifications est bien
			/// administrateur
			if (isOK || credentialRepository.isAdmin(byUserId)) {
				// Récupération de l'utilisateur en base de données.
				Credential user = credentialRepository.findById(forUserId).get();

				if (userlogin != null) {
					user.setLogin(userlogin);
				}
				if (newPassword != null) {
					setPassword(newPassword, user);
				}
				if (firstname != null) {
					user.setDisplayFirstname(firstname);
				}
				if (lastname != null) {
					user.setDisplayLastname(lastname);
				}
				if (email != null) {
					user.setEmail(email);
				}
				if (is_admin != null) {
					int is_adminInt = 0;
					if ("1".equals(is_admin))
						is_adminInt = 1;

					user.setIsAdmin(is_adminInt);
				}
				if (is_designer != null) {
					int is_designerInt = 0;
					if ("1".equals(is_designer))
						is_designerInt = 1;

					user.setIsDesigner(is_designerInt);
				}
				if (active != null) {
					int activeInt = 0;
					if ("1".equals(active))
						activeInt = 1;

					user.setActive(activeInt);
				}
				if (other != null) {
					user.setOther(other);
				}

				credentialRepository.save(user);

				if (hasSubstitute != null) {
					CredentialSubstitution subst = new CredentialSubstitution();
					/// FIXME: More complete rule to use
					CredentialSubstitutionId csId = new CredentialSubstitutionId();
					// id=0, don't check who this person can substitute (except root)
					csId.setId(0L);
					csId.setCredential(user);
					csId.setType("USER");

					if ("1".equals(hasSubstitute)) {
						subst.setId(csId);
						credentialSubstitutionRepository.save(subst);
					} else if ("0".equals(hasSubstitute)) {
						credentialSubstitutionRepository.deleteById(csId);
					}
				}
			} else {
				throw new GenericBusinessException("Not authorized");
			}
		}

		result1 = "" + forUserId;

		return result1;
	}

	public boolean isAdmin(Long userId) {
		return credentialRepository.isAdmin(userId);
	}

	public boolean isCreator(Long userId) {
		return credentialRepository.isCreator(userId);
	}

	/**
	 * Check if user password is the right one
	 *
	 * @param passwd Password
	 * @return bool result
	 */
	public boolean checkPassword(Long userId, String passwd) {
		if (userId == null || passwd.length() < CredentialRepository.PASSWORD_LENGTH) {
			log.error("Fatal Error : illegal checkPassword parameters");
			throw new RuntimeException();
		}
		Credential cr = credentialRepository.findActiveById(userId);
		return cr != null ? authenticate(passwd.toCharArray(), cr.getPassword()) : false;
	}

	public String changeUserInfo(Long byUserId, Long forUserId, String xmlUser) throws BusinessException {
		if (byUserId != forUserId)
			throw new GenericBusinessException("Not authorized");

		if (credentialRepository.existsById(forUserId))
			throw new GenericBusinessException("Unknown user id");

		String result1 = null;
		String currentPassword = null;
		String newPassword = null;
		String email = null;
		String firstname = null;
		String lastname = null;

		// Parse input
		Document doc;
		Element userInfos = null;
		try {
			doc = DomUtils.xmlString2Document(xmlUser, new StringBuffer());
			userInfos = doc.getDocumentElement();
		} catch (Exception e) {
			e.printStackTrace();
		}

		NodeList children = userInfos.getChildNodes();

		if (userInfos.getNodeName().equals("user")) {

			/// Get parameters
			for (int y = 0; y < children.getLength(); y++) {
				if (children.item(y).getNodeName().equals("prevpass")) {
					currentPassword = DomUtils.getInnerXml(children.item(y));
				} else if (children.item(y).getNodeName().equals("password")) {
					newPassword = DomUtils.getInnerXml(children.item(y));
				} else if (children.item(y).getNodeName().equals("email")) {
					email = DomUtils.getInnerXml(children.item(y));
				} else if (children.item(y).getNodeName().equals("firstname")) {
					firstname = DomUtils.getInnerXml(children.item(y));
				} else if (children.item(y).getNodeName().equals("lastname")) {
					lastname = DomUtils.getInnerXml(children.item(y));
				}
			}

			try {
				changeUserPassword(forUserId, currentPassword, newPassword);
				log.info(String.format("User with id  [%s] has changed his password\n", forUserId));
			} catch (AuthenticationException e) {
				throw e;
			} catch (BusinessException e) {
				// L'utilisation du même mot de passe dans cette méthode n'est pas interdite
				// donc on continue.
			}

			try {
				Credential cr = credentialRepository.findById(forUserId).get();

				if (email != null) {
					cr.setEmail(email);
				}
				if (firstname != null) {
					cr.setDisplayFirstname(firstname);
				}
				if (lastname != null) {
					cr.setDisplayLastname(lastname);
				}
				credentialRepository.save(cr);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		result1 = "" + forUserId;
		return result1;
	}

	/**
	 * Crée le role
	 * 
	 * @param portfolioId
	 * @param role
	 * @param userId
	 * @return
	 */
	public Long addRole(UUID portfolioId, String role, Long userId) throws BusinessException {
		Long groupId = 0L;
		Node rootNode = portfolioRepository.getPortfolioRootNode(portfolioId);

		if (!credentialRepository.isAdmin(userId)
				&& !credentialRepository.isDesigner(userId, rootNode.getId())
				&& !credentialRepository.isCreator(userId))
			throw new GenericBusinessException("No admin right");

		try {
			GroupRightInfo gri = groupRightInfoRepository.getByPortfolioAndLabel(portfolioId, role);

			if (gri != null) {
				groupId = gri.getGroupInfo().getId();
			} else {
				GroupRightInfo ngri = new GroupRightInfo(new Portfolio(portfolioId), role);
				groupRightInfoRepository.save(ngri);

				GroupInfo gi = new GroupInfo(ngri.getId(), 1L, role);
				groupInfoRepository.save(gi);

				groupId = gi.getId();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return groupId;
	}

	public void addUserToGroup(Long byUser, Long forUser, Long groupId) throws BusinessException {
		if (!credentialRepository.isAdmin(byUser))
			throw new GenericBusinessException("403 FORBIDDEN : No admin right");

		GroupUserId gid = new GroupUserId();
		gid.setCredential(new Credential(forUser));
		gid.setGroupInfo(new GroupInfo(groupId));

		if (!groupUserRepository.existsById(gid)) {
			groupUserRepository.save(new GroupUser(gid));
		}
	}

	@Override
	public boolean addUserInCredentialGroups(Long userId, List<Long> credentialGroupIds) {
		boolean added = true;
		CredentialGroupMembers cgm = null;
		try {
			for (Long credentialGroupId : credentialGroupIds) {
				cgm = new CredentialGroupMembers(
						new CredentialGroupMembersId(new CredentialGroup(credentialGroupId), new Credential(userId)));
				credentialGroupMembersRepository.save(cgm);
			}
		} catch (Exception e) {
			added = false;
		}
		return added;
	}

	public String addUserRole(Long byUserId, Long rrgid, Long forUser) throws BusinessException {
		if (!credentialRepository.isAdmin(byUserId) && !groupRightInfoRepository.isOwner(byUserId, rrgid))
			throw new GenericBusinessException("403 FORBIDDEN : No admin right");

		// Vérifie si un group_info/grid existe
		GroupInfo gi = groupInfoRepository.getGroupByGrid(rrgid);

		if (gi == null) {
			// Copie de RRG vers group_info
			GroupRightInfo gri = groupRightInfoRepository.findById(rrgid).get();

			groupInfoRepository.save(new GroupInfo(gri, gri.getOwner(), gri.getLabel()));
		}

		// Ajout de l'utilisateur
		addUserToGroup(byUserId, forUser, gi.getId());
		return "user " + forUser + " rajoute au groupd gid " + gi.getId() + " pour correspondre au groupRight grid "
				+ rrgid;
	}

	public String[] postCredentialFromXml(String login, String password, String substitute) {
		String[] returnValue = null;
		Long uid = 0L;
		Long subuid = 0L;
		try {
			Credential c = credentialRepository.findByLogin(login);

			if (c != null) {
				if (!authenticate(password.toCharArray(), c.getPassword()))
					return returnValue;
				else
					uid = c.getId();
			} else {
				return returnValue;
			}

			if (substitute != null) {
				/// Specific lenient substitution rule
				CredentialSubstitution cs = credentialSubstitutionRepository.getSubstitutionRule(uid, 0L, "USER");

				if (cs != null) {
					// User can get "any" account, except admin one
					Credential cr = credentialRepository.findByLoginAndAdmin(substitute, 0);
					if (cr != null)
						subuid = cr.getId();
				} else {
					/// General rule, when something specific is written in 'id', with USER or GROUP
					subuid = credentialSubstitutionRepository.getSubuidFromUserType(substitute, uid);
					if (subuid == null)
						subuid = credentialSubstitutionRepository.getSubuidFromGroupType(substitute, uid);
				}
			}

			returnValue = new String[5];
			returnValue[1] = login; // login
			returnValue[2] = Long.toString(uid); // User id
			returnValue[4] = Long.toString(subuid); // Substitute
			Credential cr = null;

			if (!(subuid == null || subuid == 0L)) {
				returnValue[3] = substitute;
				cr = credentialRepository.findByLogin(substitute);
			} else {
				returnValue[3] = "";
				cr = credentialRepository.findByLogin(login);
			}

			returnValue[0] = "<credential>";
			returnValue[0] += DomUtils.getXmlElementOutput("useridentifier", cr.getLogin());
			returnValue[0] += DomUtils.getXmlElementOutput("token", cr.getToken());
			returnValue[0] += DomUtils.getXmlElementOutput("firstname", cr.getDisplayFirstname());
			returnValue[0] += DomUtils.getXmlElementOutput("lastname", cr.getDisplayLastname());
			returnValue[0] += DomUtils.getXmlElementOutput("admin", String.valueOf(cr.getIsAdmin()));
			returnValue[0] += DomUtils.getXmlElementOutput("designer", String.valueOf(cr.getIsDesigner()));
			returnValue[0] += DomUtils.getXmlElementOutput("email", cr.getEmail());
			returnValue[0] += DomUtils.getXmlElementOutput("other", cr.getOther());
			returnValue[0] += "</credential>";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return returnValue;
	}

	public boolean userHasRole(long userId, long roleId) {
		return groupUserRepository.hasRole(userId, roleId);
	}

	public void removeRole(Long userId, Long groupRightInfoId) throws Exception {
		if (!credentialRepository.isAdmin(userId) && !groupRightInfoRepository.isOwner(userId, groupRightInfoId))
			throw new GenericBusinessException("403 FORBIDDEN : no admin rights");

		groupRightInfoRepository.deleteById(groupRightInfoId);
	}

	public void removeUserRole(Long userId, Long groupRightInfoId) throws BusinessException {
		if (!credentialRepository.isAdmin(userId) && !groupRightInfoRepository.isOwner(userId, groupRightInfoId))
			throw new GenericBusinessException("403 FORBIDDEN : no admin rights");

		try {
			groupUserRepository.delete(groupUserRepository.getByUserAndRole(userId, groupRightInfoId));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void removeUsersFromRole(Long userId, UUID portfolioId) throws Exception {
		if (!credentialRepository.isAdmin(userId))
			throw new GenericBusinessException("403 FORBIDDEN : no admin rights");
		groupUserRepository.deleteByPortfolio(portfolioId);
	}

	public void removeRights(Long userId, Long groupId) throws BusinessException {
		if (!credentialRepository.isAdmin(userId))
			throw new GenericBusinessException("403 FORBIDDEN : no admin rights");

		groupInfoRepository.deleteById(groupId);
	}

	public Long changeRole(Long userId, Long rrgId, String xmlRole)
			throws Exception {
		if (!credentialRepository.isAdmin(userId) && !groupRightInfoRepository.isOwner(userId, rrgId))
			throw new GenericBusinessException("403 FORBIDDEN, no admin rights");

		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		documentBuilder = documentBuilderFactory.newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(xmlRole));
		document = documentBuilder.parse(is);

		NodeList labelNodes = document.getElementsByTagName("label");
		org.w3c.dom.Node labelNode = labelNodes.item(0);

		GroupRightInfo gri = groupRightInfoRepository.findById(rrgId).get();

		if (labelNode != null) {
			org.w3c.dom.Node labelText = labelNode.getFirstChild();
			if (labelText != null) {
				gri.setLabel(labelText.getNodeValue());
			}
		}

		NodeList portfolioNodes = document.getElementsByTagName("portfolio");
		Element portfolioNode = (Element) portfolioNodes.item(0);
		if (portfolioNode != null) {
			gri.setPortfolio(new Portfolio(UUID.fromString(portfolioNode.getAttribute("id"))));
		}

		groupRightInfoRepository.save(gri);

		return gri.getId();
	}

	public String addUsersToRole(Long userId, Long rrgid, String xmlUser) throws BusinessException {
		if (!credentialRepository.isAdmin(userId) && !groupRightInfoRepository.isOwner(userId, rrgid))
			throw new GenericBusinessException("403 FORBIDDEN : no admin right");

		String value = "";
		/// Parse data
		DocumentBuilder documentBuilder;
		Document document = null;
		try {
			DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
			documentBuilder = documentBuilderFactory.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(xmlUser));
			document = documentBuilder.parse(is);
		} catch (Exception e) {
			e.printStackTrace();
		}

		/// Problème de parsage
		if (document == null)
			return value;

		try {
			Element root = document.getDocumentElement();

			/// ajout des utilisateurs
			NodeList users = root.getElementsByTagName("user");
			Long uid = null;
			String uidl = null;
			Element user = null;
			for (int j = 0; j < users.getLength(); ++j) {
				user = (Element) users.item(j);
				uidl = user.getAttribute("id");
				uid = Long.valueOf(uidl);
				addUserRole(userId, rrgid, uid);
			}
		} catch (Exception e) {
		}

		return value;
	}

	@Override
	public Credential authenticateUser(String login, String password) throws AuthenticationException {
		Credential user = credentialRepository.findByLogin(login);
		if (user != null) {
			if (!authenticate(password.toCharArray(), user.getPassword())) {
				throw new AuthenticationException("User_password_incorrect");
			}
		} else {
			throw new AuthenticationException("User_loginId_unknown", login);
		}
		return user;
	}

	@Override
	public void deleteUserFromCredentialGroup(Long userId, Long credentialGroupId) {
		credentialGroupMembersRepository.deleteUserFromGroup(userId, credentialGroupId);
	}

}
