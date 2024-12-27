package net.oneki.mtac.util.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.oneki.mtac.config.Constants;
import net.oneki.mtac.model.entity.Schema;
import net.oneki.mtac.model.entity.Tenant;
import net.oneki.mtac.model.entity.iam.Role;

@RequiredArgsConstructor
@ToString
public class Cache {
  private static Cache instance;
  public static Cache getInstance() {
    return instance;
  }

  protected Map<Integer, Schema> schemas = new HashMap<>();
  protected Map<Integer, Tenant> tenants = new HashMap<>();
  protected Map<String, Integer> schemaIds = new HashMap<>();
  protected Map<String, Integer> tenantIds = new HashMap<>();
  protected Map<Integer, Role> roles = new HashMap<>();

  @PostConstruct
  public void init() {
    addSchema(Schema.builder()
      .id(Constants.SCHEMA_SCHEMA_ID)
      .urn(String.format("urn:%s:%s:%s", Constants.TENANT_ROOT_LABEL, Constants.SCHEMA_SCHEMA_LABEL, Constants.SCHEMA_SCHEMA_LABEL))
      .pub(true)
      .build()
    );
    addTenant(Tenant.builder()
      .id(Constants.TENANT_ROOT_ID)
      .urn(String.format("urn::%s:%s", Constants.TENANT_ROOT_SCHEMA_LABEL, Constants.TENANT_ROOT_LABEL))
      .pub(false)
      .build()
    );
    instance = this;
  }

  public Role getRoleById(Integer id) {
    return roles.get(id);
  }

  public void addRole(Role role) {
    if (role != null) {
      roles.put(role.getId(), role);
    }
  }

  public void deleteRole(Integer roleId) {
    Role role = roles.get(roleId);
    if (role != null) {
      roles.remove(roleId);
    }
  }

  public Map<Integer, Role> getRoles() {
    return roles;
  }

  public void setRoles(List<Role> roles) {
    this.roles = roles.stream().collect(Collectors.toMap(
      Role::getId, Function.identity())
    );
  }

  public void addSchema(Schema schema) {
    if (schema != null) {
      schemas.put(schema.getId(), schema);
      if (schema.getLabel() != null) {
        schemaIds.put(schema.getLabel(), schema.getId());
      }
    }
  }

  public void addTenant(Tenant tenant) {
    if (tenant != null) {
      tenants.put(tenant.getId(), tenant);
      if (tenant.getLabel() != null) {
        tenantIds.put(tenant.getLabel(), tenant.getId());
      }
    }
  }

  public void deleteSchema(Integer schemaId) {
    Schema schema = schemas.get(schemaId);
    if (schema != null) {
      schemas.remove(schemaId);
      if (schema.getLabel() != null) {
        schemaIds.remove(schema.getLabel());
      }
    }
  }

  public void deleteTenant(Integer tenantId) {
    Tenant tenant = tenants.get(tenantId);
    if (tenant != null) {
      tenants.remove(tenantId);
      if (tenant.getLabel() != null) {
        tenantIds.remove(tenant.getLabel());
      }
    }
  }


  public Schema getSchemaById(Integer id) {
    if (schemas != null) {
      return schemas.get(id);
    }
    return null;
  }

  public Schema getSchemaByLabel(String schemaLabel) {
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

  public Tenant getTenantById(Integer id) {
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

  public Tenant getTenantByLabel(String tenantLabel) {
    var id = getTenantId(tenantLabel);
    if (id != null) {
      return getTenantById(id);
    }
    return null;
  }

  public Map<Integer, Schema> getSchemas() {
    return schemas;
  }

  public Map<Integer, Tenant> getTenants() {
    return tenants;
  }

  public void setSchemas(List<Schema> schemas) {
    this.schemas = schemas.stream().collect(Collectors.toMap(
      Schema::getId, Function.identity())
    );
    schemaIds = schemas.stream().collect(Collectors.toMap(
      Schema::getLabel, Schema::getId)
    );
  }

  public void setTenants(List<Tenant> tenants) {
    this.tenants = tenants.stream().collect(Collectors.toMap(
      Tenant::getId, Function.identity())
    );
    tenantIds = tenants.stream().collect(Collectors.toMap(
      Tenant::getLabel, Tenant::getId)
    );
  }

}
