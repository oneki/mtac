package net.oneki.mtac.model.entity;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Lookup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.framework.HasSchema;
import net.oneki.mtac.model.framework.Urn;
import net.oneki.mtac.model.security.Acl;
import net.oneki.mtac.util.cache.ResourceRegistry;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public abstract class Resource implements HasLabel, HasId, HasSchema {
	/*
	 * Internal id of the resource. Auto generated during the first insertion in the DB
	 * This id should never be exposed to the outside and is only used internally
	 */
	protected Integer id;

	@JsonProperty("@s")
	protected Integer schemaId;
	@JsonProperty("@t")
	protected Integer tenantId;
	@JsonProperty("@l")
	protected String label;

	@JsonIgnore
	protected String tenantLabel;

	/*
	 * A urn is used as a public id and uniquely identifies a resource
	 * The format is <tenant>:<schema>:<label>
	 *
	 * A tenant or a schema can't contain a colon
	 *
	 * Example: urn:root:iam.identity.user:olivier.franki@gmail.com
	 */
	@JsonProperty("@urn")
	protected String urn;

	/*
	 * A public resource is visible by any sub-tenant
	 */
	@JsonProperty("@pub")
	protected boolean pub;

	/*
	 * Indicates that the resource is actually a link to another resource
	 */
	protected boolean link;

	/*
	 * The datetime at which the resource was created
	 */
	@JsonProperty("@createdAt")
	protected Instant createdAt;

	/*
	 * The datetime at which the resource was last updated
	 */
	@JsonProperty("@updatedAt")
	protected Instant updatedAt;

	/*
	 * The email of the creator of the resource
	 */
	@JsonProperty("@createdBy")
	protected String createdBy;

	/*
	 * The email of the last updater of the resource
	 */
	@JsonProperty("@updatedBy")
	protected String updatedBy;

	/*
	 * The ACL (access control list) of the resource
	 * An ACL is a list of ACE (access control entries)
	 * An ACE is a combinaison of a idenity (user or group) and a role
	 */
	protected Acl acl;

	/*
	 * List of actions of this resource that the logged user has access to
	 */
	protected List<String> grantedActions;

	/*
	 * List of fields of this resource that the logged user has access to
	 * Used to filter some sensitive fields like password
	 */
	protected List<String> grantedFields;

	/*
	 * When a new resource is inserted, we must labelize it based on some fields
	 * of the resource
	 * Example:
	 *   organization: MyOrg
	 *   site: site1
	 *
	 *   --> The label is site1@MyOrg
	 */
	@Lookup
	public abstract String labelize();

	// public String toUrn(String tenant) {
	// return String.format("%s:%s:%s", tenant, getSchema(), labelize());
	// }

	public Integer getSchemaId() {
		return ResourceRegistry.getSchemaId(getSchema());
	}

	public Integer getTenantId() {
		return ResourceRegistry.getTenantId(getTenantLabel());
	}

	public Ref toRef() {
		return Ref.builder()
				.id(id)
				.label(getLabel())
				.schema(getSchemaId())
				.tenant(getTenantId())
				.build();
	}

	@Override
	public boolean equals(Object otherEntity) {
		if (this.getClass() == otherEntity.getClass()) {
			if (id != null && id.equals(((Resource) otherEntity).getId())) {
				return true;
			}
		}
		return super.equals(otherEntity);
	}

	@Override
	public int hashCode() {
		if (id != null) {
			return id;
		}
		return super.hashCode();
	}

	// final getter and setters
	public final Integer getId() {
		return id;
	}

	public final void setId(Integer id) {
		this.id = id;
	}

	public final String getUrn() {
		return urn;
	}

	public final void setUrn(String urn) {
		this.urn = urn;
	}

	public final String getLabel() {
		if (label != null) return label;
		if (urn != null) {
			var label = Urn.of(urn).label();
			this.label = label;
			return label;
		}
		return null;
	}

	// public final void setLabel(String label) {
	// 	if (urn == null) {
	// 		urn = "::" + label;
	// 	} else {
	// 		var startPos = urn.indexOf(":", urn.indexOf(":") + 1);
	// 		if (startPos == -1) {
	// 			urn = urn + ":" + label;
	// 		} else {
	// 			urn = urn.substring(0, startPos + 1) + label;
	// 		}
	// 	}
	// }

	public final boolean isPub() {
		return pub;
	}

	public final void setPub(boolean pub) {
		this.pub = pub;
	}

	@JsonIgnore
	public final String getSchema() {
		return ResourceRegistry.getSchemaByClass(this.getClass());
	}

	// public final void setSchema(String schema) {
	// 	if (urn == null) {
	// 		urn =  ":" + schema + ":";
	// 	} else {
	// 		var startPos = urn.indexOf(":");
	// 		if (startPos == -1) {
	// 			urn = ":" + schema + ":" + urn;
	// 		} else {
	// 			var endPos = urn.indexOf(":", startPos + 1);
	// 			if (endPos == -1) {
	// 				urn = urn + ":" + schema;
	// 			} else {
	// 				urn = urn.substring(0, startPos + 1) + schema + urn.substring(endPos);
	// 			}
	// 		}
	// 	}
	// }

	public final String getTenantLabel() {
		if (tenantLabel != null) return tenantLabel;
		if (tenantId != null) return ResourceRegistry.getTenantLabel(tenantId);
		if (urn != null) {
			var tenantLabel = Urn.of(urn).tenant();
			this.tenantLabel = tenantLabel;
			return tenantLabel;
		}
		return null;
	}

	

	// public final void setTenant(String tenantLabel) {
	// 	if (urn == null) {
	// 		urn = tenantLabel + "::";
	// 	} else {
	// 		var endPos = urn.indexOf(":");
	// 		if (endPos == -1) {
	// 			urn = tenantLabel + ":" + urn;
	// 		} else {
	// 			urn = tenantLabel + urn.substring(endPos);
	// 		}
	// 	}
	// }

	public final boolean isLink() {
		return link;
	}

	public final void setLink(boolean link) {
		this.link = link;
	}

	public final Instant getCreatedAt() {
		return createdAt;
	}

	public final void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public final Instant getUpdatedAt() {
		return updatedAt;
	}

	public final void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public final String getCreatedBy() {
		return createdBy;
	}

	public final void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public final String getUpdatedBy() {
		return updatedBy;
	}

	public final Acl getAcl() {
		return acl;
	}

	public final void setAcl(Acl acl) {
		this.acl = acl;
	}

	public final List<String> getGrantedActions() {
		return grantedActions;
	}

	public final void setGrantedActions(List<String> grantedActions) {
		this.grantedActions = grantedActions;
	}

	public final List<String> getGrantedFields() {
		return grantedFields;
	}

	public final void setGrantedFields(List<String> grantedFields) {
		this.grantedFields = grantedFields;
	}
}
