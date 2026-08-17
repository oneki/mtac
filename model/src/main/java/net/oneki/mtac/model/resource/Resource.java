package net.oneki.mtac.model.resource;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.sqids.Sqids;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import net.oneki.mtac.model.core.framework.HasSchema;
import net.oneki.mtac.model.core.resource.HasId;
import net.oneki.mtac.model.core.resource.HasLabel;
import net.oneki.mtac.model.core.resource.HasUid;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.security.Acl;
import net.oneki.mtac.model.core.util.exception.UnexpectedException;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Schema(description = "Base class for all resources Provides common fields like uid, label, timestamps, and access control.")
public abstract class Resource implements HasLabel, HasId, HasUid, HasSchema {
	public static Sqids sqids; // short UID generator from Integer (7 chars length)
	/*
	 * Internal id of the resource. Auto generated during the first insertion in the
	 * DB
	 * This id should never be exposed to the outside and is only used internally
	 */
	@Hidden
	protected Integer id;

	@Schema(description = "The unique identifier (UID) of the resource. This is a base32-encoded integer", example = "R3sT7uVw")
	protected String uid;

	@JsonProperty("s")
	@Hidden
	protected Integer schemaId;

	@JsonIgnore
	@Hidden
	protected Integer tenantId;

	@Schema(description = "A human-readable label for the resource", example = "Acme")
	protected String label;

	@Schema(description = "The label of the tenant that owns this resource", example = "Companies Resource Group")
	@JsonProperty("tenant")
	protected String tenantLabel;

	@Schema(description = "The schema label describing the type of this resource", example = "tenant.company")
	@JsonProperty("schema")
	protected String schemaLabel;

	/*
	 * A urn is used as a public id and uniquely identifies a resource
	 * The format is <tenant>:<schema>:<label>
	 *
	 * A tenant or a schema can't contain a colon
	 *
	 * Example: urn:root:iam.identity.user:olivier.franki@gmail.com
	 */
	// @JsonProperty("$urn")
	// protected String urn;

	@Schema(description = "Whether this resource is public and visible by any sub-tenant", example = "false")
	@Hidden
	protected boolean pub;

	@Schema(description = "Whether this resource is a link to another resource", example = "false")
	@Hidden
	protected boolean link;

	@JsonIgnore
	protected Integer linkId;

	@Schema(description = "The UID of the linked resource, if this resource is a link", example = "R3sT7uVw")
	@Hidden
	protected String linkUid;

	@JsonIgnore
	protected LinkType linkType;

	@Schema(description = "The type of resource as an integer identifier (Internal use only)", example = "1")
	@Builder.Default
	protected Integer resourceType = ResourceType.INTERNAL_RESOURCE;

	@Schema(description = "The datetime at which the resource was created (ISO 8601 format)", example = "2024-01-15T10:30:00Z")
	protected Instant createdAt;

	@Schema(description = "The datetime at which the resource was last updated (ISO 8601 format)", example = "2024-06-20T14:45:00Z")
	protected Instant updatedAt;

	@Schema(description = "The identifier (generally the email) of the user who created the resource", example = "admin@example.com")
	protected String createdBy;

	@Schema(description = "The identifier (generally the email) of the user who last updated the resource", example = "admin@example.com")
	protected String updatedBy;

	@Schema(description = "The ACL (Access Control List) of the resource, defining which identities (users or groups) have which roles")
	@Hidden
	protected Acl acl;

	@Schema(description = "List of actions on this resource that the logged-in user has access to", example = "[\"read\",\"update\",\"delete\"]")
	@Hidden
	protected List<String> grantedActions;

	@Schema(description = "List of fields of this resource that the logged-in user has access to. Used to filter sensitive fields like passwords", example = "[\"uid\",\"label\",\"email\"]")
	@Hidden
	protected List<String> grantedFields;

	/*
	 * When a new resource is inserted, we must labelize it based on some fields
	 * of the resource
	 * Example:
	 * organization: MyOrg
	 * site: site1
	 *
	 * --> The label is site1@MyOrg
	 */
	// @Lookup
	// public abstract String labelize();

	// public String toUrn(String tenant) {
	// return String.format("%s:%s:%s", tenant, getSchema(), labelize());
	// }
	public static void initSqids(String alphabet) {
		if (sqids == null) {
			sqids = Sqids.builder()
				.minLength(8)
				.alphabet(alphabet)
				.build();
		}
	}

	public static String toUid(Integer id) {
		if (sqids == null) {
			throw new UnexpectedException("SQIDS_NOT_INITIALIZED",
					"Sqids is not initialized. Please call initSqids() before using this method.");
		}
		if (id == null) {
			return null;
		}
		return sqids.encode(Arrays.asList(Long.valueOf(id)));
	}

		public static String toUid(Long id) {
		if (sqids == null) {
			throw new UnexpectedException("SQIDS_NOT_INITIALIZED",
					"Sqids is not initialized. Please call initSqids() before using this method.");
		}
		if (id == null) {
			return null;
		}
		return sqids.encode(Arrays.asList(Long.valueOf(id)));
	}

	public static Integer fromUid(String uid) {
		if (sqids == null) {
			throw new UnexpectedException("SQIDS_NOT_INITIALIZED",
					"Sqids is not initialized. Please call initSqids() before using this method.");
		}
		if (uid == null) {
			return null;
		}
		var decoded = sqids.decode(uid);
		if (decoded.size() != 1) {
			throw new UnexpectedException("SQIDS_DECODE_ERROR",
					"Sqids decode error. Expected 1 value, got " + decoded.size());
		}
		return decoded.get(0).intValue();
	}

	public Ref toRef() {
		return Ref.builder()
				.id(id)
				.label(getLabel())
				.schema(getSchemaId())
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
		this.uid = toUid(id);
	}

	// public final Schema getSchema() {
	// 	throw new UnsupportedOperationException("getSchema is not supported in this resource");
	// }

	// public final void setSchema(Schema schema) {
	// 	throw new UnsupportedOperationException("setSchema is not supported in this resource");
	// }

	public final String getUid() {
		return uid;
	}

	public final void setUid(String uid) {
		this.id = fromUid(uid);
		this.uid = uid;
	}	

	public final Integer getLinkId() {
		return linkId;
	}

	public final void setLinkId(Integer linkId) {
		this.linkId = linkId;
		this.linkUid = toUid(linkId);

	}

	public final String getLinkUid() {
		return linkUid;
	}

	public final void setLinkUid(String linkUid) {
		this.linkId = fromUid(linkUid);
		this.linkUid = linkUid;
	}

	public final LinkType getLinkType() {
		return linkType;
	}

	public final void setLinkType(LinkType linkType) {
		this.linkType = linkType;
	}

	@JsonIgnore
	public boolean isLinkRef() {
		return linkId != null && linkType != null && linkType == LinkType.Ref;
	}

	// public final String getUrn() {
	// 	return urn;
	// }

	// public final void setUrn(String urn) {
	// 	this.urn = urn;
	// }

	public final String getLabel() {
		return label;
	}

	public final void setLabel(String label) {
		this.label = label;
	}

	// public final void setLabel(String label) {
	// if (urn == null) {
	// urn = "::" + label;
	// } else {
	// var startPos = urn.indexOf(":", urn.indexOf(":") + 1);
	// if (startPos == -1) {
	// urn = urn + ":" + label;
	// } else {
	// urn = urn.substring(0, startPos + 1) + label;
	// }
	// }
	// }

	public final boolean isPub() {
		return pub;
	}

	public final void setPub(boolean pub) {
		this.pub = pub;
	}

	// public final void setSchema(String schema) {
	// if (urn == null) {
	// urn = ":" + schema + ":";
	// } else {
	// var startPos = urn.indexOf(":");
	// if (startPos == -1) {
	// urn = ":" + schema + ":" + urn;
	// } else {
	// var endPos = urn.indexOf(":", startPos + 1);
	// if (endPos == -1) {
	// urn = urn + ":" + schema;
	// } else {
	// urn = urn.substring(0, startPos + 1) + schema + urn.substring(endPos);
	// }
	// }
	// }
	// }

	public final String getTenantLabel() {
		return tenantLabel;
	}

	// public final Tenant getTenant() {
	// 	throw new UnsupportedOperationException("getTenant is not supported in this resource");
	// }

	// public final void setTenant(Tenant tenant) {
	// 	throw new UnsupportedOperationException("setTenant is not supported in this resource");
	// }

	// @JsonIgnore
	// protected final String getTenantSuffix() {
	// 	if (tenantLabel != null)
	// 		return "@" + toSuffix(tenantLabel);
	// 	throw new RuntimeException("Tenant label is not set for entity " + getClass().getSimpleName() + " with id " + id);
	// }

	// public static String toSuffix(String label) {
	// 	// Example: label = "site1@MyOrg"
	// 	// --> suffix = "site1.MyOrg"
	// 	return label.replace("@", ".");
	// }

	public final String getSchemaLabel() {
		return schemaLabel;
	}

	// public final void setTenant(String tenantLabel) {
	// if (urn == null) {
	// urn = tenantLabel + "::";
	// } else {
	// var endPos = urn.indexOf(":");
	// if (endPos == -1) {
	// urn = tenantLabel + ":" + urn;
	// } else {
	// urn = tenantLabel + urn.substring(endPos);
	// }
	// }
	// }

	public final boolean isLink() {
		return linkId != null;
	}

	public final void setLink(boolean link) {
		throw new RuntimeException("Link can't be set directly. Use setLinkId instead.");
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
