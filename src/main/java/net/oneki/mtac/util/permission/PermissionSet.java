package net.oneki.mtac.util.permission;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class PermissionSet {
    private Set<String> permissions = new HashSet<String>();

    public PermissionSet(String... permissions) {
        for (var permission : permissions) {
            this.permissions.add(permission);
        }
    }

    public PermissionSet add(String... permissions) {
        var permissionSet = new PermissionSet();
        permissionSet.getPermissions().addAll(this.permissions);
        for (var permission : permissions) {
            permissionSet.getPermissions().add(permission);
        }
        return permissionSet;
    }

    public PermissionSet remove(String... permissions) {
        var permissionSet = new PermissionSet();
        permissionSet.getPermissions().addAll(this.permissions);
        for (var permission : permissions) {
            permissionSet.getPermissions().remove(permission);
        }
        return permissionSet;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
