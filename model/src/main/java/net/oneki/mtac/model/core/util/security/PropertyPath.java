package net.oneki.mtac.model.core.util.security;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import net.oneki.mtac.model.core.resource.Ref;
import net.oneki.mtac.model.core.util.security.PropertyPath;

@Data
public class PropertyPath {

    protected String schemaLabel;
    protected String permission;

    public static PropertyPath any() {
        return new PropertyPath("*", "*");
    }

    public static PropertyPath anyProperties(String schemaLabel) {
        return new PropertyPath(schemaLabel, "*");
    }

    public static PropertyPath anyProperties(Ref schema) {
        return new PropertyPath(schema.getLabel(), "*");
    }

    public static PropertyPath of(Ref schema, String permission) {
        return new PropertyPath(schema.getLabel(), permission);
    }

    public static PropertyPath of(String schemaLabel, String permission) {
        return new PropertyPath(schemaLabel, permission);
    }

    /**
     * permission is a string in the form of schemaLabel|permissionLabel or '*'
     * Example: "tenant.company|create", "iam.identity.user|reset_password", "*"
     * @param permissions
     * @return
     */
    public static List<PropertyPath> of(List<String> permissions) {
        return permissions.stream()
            .map(permission -> {
                String[] parts = permission.split("\\|");
                String schemaLabel = parts[0];
                String permissionLabel = "*";
                if (parts.length > 1) {
                    permissionLabel = parts[1];
                }
                return new PropertyPath(schemaLabel, permissionLabel);
            })
            .collect(Collectors.toList());
    }

    public static PropertyPath of(String path) {
        String[] parts = path.split("\\|");
        String schemaLabel = parts[0];
        String permission = "*";
        if (parts.length > 1) {
            permission = parts[1];
        }
        return new PropertyPath(schemaLabel, permission);
    }

    public PropertyPath(String schemaLabel, String permission) {
        this.schemaLabel = schemaLabel;
        this.permission = permission;
    }

    public boolean contains(PropertyPath path) {
        if (!path.getSchemaLabel().matches(getSchemaLabel().replaceAll("\\.", "\\\\.").replaceAll("\\*", ".*"))) {
            return false;
        }
        if (!path.getPermission().matches(getPermission().replaceAll("\\*", ".*"))) {
            return false;
        }
        return true;
    }

    public String toString() {
        if (schemaLabel.equals("*")) {
            return "*";
        }
        return schemaLabel + "|" + permission;
    }

}
