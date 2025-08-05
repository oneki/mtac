package net.oneki.mtac.resource.iam.identity.application;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.framework.cache.TokenRegistry;
import net.oneki.mtac.framework.repository.ResourceRepository;
import net.oneki.mtac.framework.repository.TokenRepository;
import net.oneki.mtac.framework.service.JwtTokenService;
import net.oneki.mtac.framework.util.security.PasswordUtil;
import net.oneki.mtac.model.core.config.MtacProperties;
import net.oneki.mtac.model.core.util.exception.BusinessException;
import net.oneki.mtac.model.core.util.security.Claims;
import net.oneki.mtac.model.core.util.security.SecurityContext;
import net.oneki.mtac.model.resource.Resource;
import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.application.BaseApplicationUpsertRequest;
import net.oneki.mtac.model.resource.iam.identity.group.Group;
import net.oneki.mtac.resource.ResourceService;
import net.oneki.mtac.resource.iam.RoleRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupMembershipRepository;
import net.oneki.mtac.resource.iam.identity.group.GroupRepository;
import net.oneki.mtac.resource.tenant.DefaultTenantService;

@Service("mtacApplicationService")
@RequiredArgsConstructor
public abstract class ApplicationService<U extends BaseApplicationUpsertRequest<? extends Group>, E extends Application>
    extends ResourceService<U, E> {
  // private final UserRepository userRepository;
  protected GroupMembershipRepository groupMembershipRepository;
  protected GroupRepository groupRepository;
  // private final TenantRepository tenantRepository;
  protected JwtTokenService tokenService;
  protected ResourceRepository resourceRepository;
  protected RoleRepository roleRepository;
  protected SecurityContext securityContext;
  protected TokenRegistry tokenRegistry;
  protected TokenRepository tokenRepository;
  protected PasswordUtil passwordUtil;
  protected MtacProperties mtacProperties;
  protected DefaultTenantService tenantService;

  @Value("${mtac.iam.tenants-in-idtoken.enabled:false}")
  private boolean storeTenantsInIdToken;
  @Value("${mtac.iam.tenants-in-idtoken.schema-labels:}")
  private Set<String> storeTenantsSchemaLabels;


  @Autowired
  public final void setGroupMembershipRepository(GroupMembershipRepository groupMembershipRepository) {
    this.groupMembershipRepository = groupMembershipRepository;
  }

  @Autowired
  public final void setGroupRepository(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  @Autowired
  public final void setTokenService(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public final void setResourceRepository(ResourceRepository resourceRepository) {
    this.resourceRepository = resourceRepository;
  }

  @Autowired
  public final void setRoleRepository(RoleRepository roleRepository) {
    this.roleRepository = roleRepository;
  }

  @Autowired
  public final void setSecurityContext(SecurityContext securityContext) {
    this.securityContext = securityContext;
  }

  @Autowired
  public final void setTokenRegistry(TokenRegistry tokenRegistry) {
    this.tokenRegistry = tokenRegistry;
  }

  @Autowired
  public final void setTokenRepository(TokenRepository tokenRepository) {
    this.tokenRepository = tokenRepository;
  }

  @Autowired
  public final void setPasswordUtil(PasswordUtil passwordUtil) {
    this.passwordUtil = passwordUtil;
  }

  @Autowired
  public final void setMtacProperties(MtacProperties mtacProperties) {
    this.mtacProperties = mtacProperties;
  }

  @Autowired
  public final void setTenantService(DefaultTenantService defaultTenantService) {
    this.tenantService = defaultTenantService;
  }

  @Value("${jwt.expiration-sec:86400}")
  protected int accessTokenExpirationSec;

  @Value("${jwt.refresh-expiration-sec:2678400}")
  protected int refreshTokenExpirationSec;

  public E getByLabel(String tenantLabel, String userLabel) {
    return resourceRepository.getByLabel(userLabel, tenantLabel, getEntityClass());
  }

  
  public Claims userinfo() {
    return userinfo(false);
  }

  public Claims userinfo(boolean forceRefresh) {
    return userinfo(securityContext.getSubject(), forceRefresh);
  }

  public Claims userinfo(String sub, boolean forceRefresh) {
    var claims = tokenRegistry.get(sub);
    if (claims != null) {
      return claims;
    }
    var application = getByUidUnsecure(sub);
    return userinfo(application, forceRefresh);
  }

  public Claims userinfo(Application application, boolean forceRefresh) {
    return userinfo(application, false, forceRefresh);
  }

  public Claims userinfo(Application application, boolean includeRoleName, boolean forceRefresh) {
    var claims = tokenRegistry.get(application.getUid());
    if (claims != null) {
      return claims;
    } else {
      claims = new Claims();
    }
    claims.put("sub", application.getUid());
    claims.put("username", application.getLabel());
    claims.put("name", application.getName());

    claims.putAll(tenantService.getTenantClaims(application, includeRoleName).asClaims());

    fillUserInfo(claims, application);
    // store the token in the cache
    tokenRegistry.put(application.getUid(), claims);
    var expireAt = Instant.now().plusSeconds(application.getTokenExpiresIn() != null
            ? application.getTokenExpiresIn() : accessTokenExpirationSec);
    tokenRepository.upsertToken(application.getId(), application.getUid(), claims, expireAt);

    return claims;
  }

  protected void fillUserInfo(Claims claims, Application application) {
  }

  public E create(U request) {
    var userEntity = toCreateEntity(request);
    if (userEntity.getPassword() == null) {
      throw new BusinessException("INVALID_PASSWORD", "Password cannot be blank");
    } else {
      userEntity.setPassword(passwordUtil.hash(request.getPassword()));
    }

    var result = resourceRepository.create(userEntity);
    if (userEntity.getMemberOf() != null) {
      for (var group : userEntity.getMemberOf()) {
        groupMembershipRepository.create(group.toRef(), result.toRef());
      }
    }

    return result;

  }

  public E update(String urn, U request) {
    var userEntity = toUpdateEntity(urn, request);
    if (request.getPassword() != null) {
      userEntity.setPassword(passwordUtil.hash(request.getPassword()));
    }
    resourceRepository.update(userEntity);
    return userEntity;
  }

  public Set<Integer> listGroupSids(@NonNull Resource user) {
    Set<Integer> groupSids = groupMembershipRepository.listByRight(user.toRef());
    if (groupSids.size() > 0) {
      var nestedGroupSids = groupRepository.listNestedGroupSidsUnsecure(groupSids);
      groupSids.addAll(nestedGroupSids);
    }
    return groupSids;
  }

}
