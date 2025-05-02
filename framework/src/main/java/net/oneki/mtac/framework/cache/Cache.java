package net.oneki.mtac.framework.cache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.oneki.mtac.model.resource.Tenant;
import net.oneki.mtac.model.resource.iam.Role;
import net.oneki.mtac.model.resource.schema.Schema;

public class Cache {

  protected Map<Integer, Schema> schemas = new HashMap<>();
  protected Map<Integer, Tenant> tenants = new HashMap<>();
  protected Map<String, Integer> schemaIds = new HashMap<>();
  protected Map<String, Integer> tenantIds = new HashMap<>();
  protected Map<Integer, Role> roles = new HashMap<>();
  private Map<Integer, Set<Integer>> tenantAncestors = new HashMap<>();
  private Map<Integer, Set<Integer>> tenantDescendants = new HashMap<>();

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

  public Set<Integer> getTenantAncestors(Integer tenantId) {
    return tenantAncestors.getOrDefault(tenantId, new HashSet<>());
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

  public void setTenantAncestor(Integer tenantId, Set<Integer> ancestors) {
    if (tenantId != null) {
      tenantAncestors.put(tenantId, ancestors);
      for (var ancestor: ancestors) {
        var descendants = tenantDescendants.getOrDefault(ancestor, new HashSet<Integer>());
        descendants.add(tenantId);
        tenantDescendants.put(ancestor, descendants);
      }
    }
  }

  public void setTenantAncestors(Map<Integer, Set<Integer>> tenantAncestors) {
    for (var entry: tenantAncestors.entrySet()) {
      setTenantAncestor(entry.getKey(), entry.getValue());
    }
  }

  public void addTenantAncestor(Integer tenantId, Integer ancestorId) {
    if (tenantId != null && ancestorId != null) {
      var ancestors = tenantAncestors.getOrDefault(tenantId, new HashSet<>());
      ancestors.add(ancestorId);
      tenantAncestors.put(tenantId, ancestors);
    }
  }

  public void addTenantDescendant(Integer tenantId, Integer descendantId) {
    if (tenantId != null && descendantId != null) {
      var descendants = tenantDescendants.getOrDefault(tenantId, new HashSet<>());
      descendants.add(descendantId);
      tenantDescendants.put(tenantId, descendants);
    }
  }

  public void deleteTenantAncestor(Integer tenantId) {
    if (tenantId != null) {
      var ancestors = tenantAncestors.get(tenantId);
      if (ancestors != null) {
        for (var ancestor: ancestors) {
          var descendants = tenantDescendants.get(ancestor);
          if (descendants != null) {
            descendants.remove(tenantId);
          }
        }
        tenantAncestors.remove(tenantId);
      }
    }
  }

}
