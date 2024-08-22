package net.oneki.mtac.util.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.oneki.mtac.config.Constants;
import net.oneki.mtac.model.entity.SchemaEntity;
import net.oneki.mtac.model.entity.TenantEntity;
import net.oneki.mtac.model.entity.iam.RoleEntity;

@Component
@RequiredArgsConstructor
@ToString
public class Cache {
  private static Cache instance;
  public static Cache getInstance() {
    return instance;
  }

  protected Map<Integer, SchemaEntity> schemas = new HashMap<>();
  protected Map<Integer, TenantEntity> tenants = new HashMap<>();
  protected Map<String, Integer> schemaIds = new HashMap<>();
  protected Map<String, Integer> tenantIds = new HashMap<>();
  protected Map<Integer, RoleEntity> roles = new HashMap<>();

  @PostConstruct
  public void init() {
    addSchema(SchemaEntity.builder()
      .id(Constants.SCHEMA_SCHEMA_ID)
      .urn(SchemaEntity.asUrn(Constants.SCHEMA_SCHEMA_LABEL))
      .pub(true)
      .build()
    );
    addTenant(TenantEntity.builder()
      .id(Constants.TENANT_ROOT_ID)
      .urn(String.format("%s:%s:%s", Constants.TENANT_ROOT_LABEL, Constants.TENANT_ROOT_SCHEMA_LABEL, Constants.TENANT_ROOT_LABEL))
      .pub(false)
      .build()
    );
    instance = this;
  }

  public RoleEntity getRoleById(Integer id) {
    return roles.get(id);
  }

  public void addRole(RoleEntity role) {
    if (role != null) {
      roles.put(role.getId(), role);
    }
  }

  public void deleteRole(Integer roleId) {
    RoleEntity role = roles.get(roleId);
    if (role != null) {
      roles.remove(roleId);
    }
  }

  public Map<Integer, RoleEntity> getRoles() {
    return roles;
  }

  public void setRoles(List<RoleEntity> roles) {
    this.roles = roles.stream().collect(Collectors.toMap(
      RoleEntity::getId, Function.identity())
    );
  }

  public void addSchema(SchemaEntity schema) {
    if (schema != null) {
      schemas.put(schema.getId(), schema);
      if (schema.getLabel() != null) {
        schemaIds.put(schema.getLabel(), schema.getId());
      }
    }
  }

  public void addTenant(TenantEntity tenant) {
    if (tenant != null) {
      tenants.put(tenant.getId(), tenant);
      if (tenant.getLabel() != null) {
        tenantIds.put(tenant.getLabel(), tenant.getId());
      }
    }
  }

  public void deleteSchema(Integer schemaId) {
    SchemaEntity schema = schemas.get(schemaId);
    if (schema != null) {
      schemas.remove(schemaId);
      if (schema.getLabel() != null) {
        schemaIds.remove(schema.getLabel());
      }
    }
  }

  public void deleteTenant(Integer tenantId) {
    TenantEntity tenant = tenants.get(tenantId);
    if (tenant != null) {
      tenants.remove(tenantId);
      if (tenant.getLabel() != null) {
        tenantIds.remove(tenant.getLabel());
      }
    }
  }


  public SchemaEntity getSchemaById(Integer id) {
    if (schemas != null) {
      return schemas.get(id);
    }
    return null;
  }

  public SchemaEntity getSchemaByLabel(String schemaLabel) {
    var id = getSchemaId(schemaLabel);
    if (id != null) {
      return getSchemaById(id);
    }
    return null;
  }

  public Integer getSchemaId(String schemaLabel) {
    if (schemaIds != null) {
      return schemaIds.get(schemaLabel);
    }
    return null;
  }

  public TenantEntity getTenantById(Integer id) {
    if (tenants != null) {
      return tenants.get(id);
    }
    return null;
  }

  public Integer getTenantId(String tenantLabel) {
    if (tenantIds != null) {
      return tenantIds.get(tenantLabel);
    }
    return null;
  }

  public TenantEntity getTenantByLabel(String tenantLabel) {
    var id = getTenantId(tenantLabel);
    if (id != null) {
      return getTenantById(id);
    }
    return null;
  }

  public Map<Integer, SchemaEntity> getSchemas() {
    return schemas;
  }

  public Map<Integer, TenantEntity> getTenants() {
    return tenants;
  }

  public void setSchemas(List<SchemaEntity> schemas) {
    this.schemas = schemas.stream().collect(Collectors.toMap(
      SchemaEntity::getId, Function.identity())
    );
    schemaIds = schemas.stream().collect(Collectors.toMap(
      SchemaEntity::getLabel, SchemaEntity::getId)
    );
  }

  public void setTenants(List<TenantEntity> tenants) {
    this.tenants = tenants.stream().collect(Collectors.toMap(
      TenantEntity::getId, Function.identity())
    );
    tenantIds = tenants.stream().collect(Collectors.toMap(
      TenantEntity::getLabel, TenantEntity::getId)
    );
  }

}
