package net.oneki.mtac.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;
import net.oneki.mtac.service.iam.identity.UserService;
import net.oneki.mtac.service.security.JwtTokenService;
import net.oneki.mtac.service.security.PermissionService;
import net.oneki.mtac.util.exception.BusinessException;

@RequiredArgsConstructor
public class PermissionResource {

    protected final UserService userService;
    protected final JwtTokenService tokenService;
    protected final PermissionService permissionService;
    
    @RequestMapping(value = "/permissions/id/{resourceId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> hasPermission(
        @PathVariable Integer resourceId, 
        @RequestParam(required = true) String permission
    ) {
        if ("create".equals(permission)) {
            throw new BusinessException("INVALID_PERMISSION", "The permission create is not supported by this API. Use /create-permissions instead");
        }

        if (permissionService.hasPermission(resourceId, permission)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }

    @RequestMapping(value = "/permissions/label/{tenantLabel}/{schemaLabel}/{resourceLabel}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> hasPermission(
        @PathVariable String tenantLabel,
        @PathVariable String schemaLabel, 
        @PathVariable String resourceLabel, 
        @RequestParam(required = true) String permission
    ) {
        if ("create".equals(permission)) {
            throw new BusinessException("INVALID_PERMISSION", "The permission create is not supported by this API. Use /create-permissions instead");
        }

        if (permissionService.hasPermission(resourceLabel, tenantLabel, schemaLabel, permission)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }

    @RequestMapping(value = "/create-permissions/id/{tenantId}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> hasCreatePermission(
        @PathVariable Integer tenantId, 
        @RequestParam(required = true) String schemaLabel
    ) {
        if (permissionService.hasCreatePermission(tenantId, schemaLabel)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }

    @RequestMapping(value = "/create-permissions/label/{tenantLabel}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> hasCreatePermission(
        @PathVariable String tenantLabel, 
        @RequestParam(required = true) String schemaLabel
    ) {
        if (permissionService.hasCreatePermission(tenantLabel, schemaLabel)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(403).build();
        }
    }
}
