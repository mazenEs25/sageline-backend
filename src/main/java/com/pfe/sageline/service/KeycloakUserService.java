package com.pfe.sageline.service;

import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.admin.realm}")
    private String realm;

    public KeycloakUserService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }


    public String createUser(String username, String email, String password,
                             String firstName, String lastName, String role) {

        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        // 1. Build user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName != null ? firstName : username);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // 2. Set password (non-temporary)
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        user.setCredentials(Collections.singletonList(credential));

        // 3. Create user in Keycloak
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            // Extract user ID from Location header
            String locationHeader = response.getHeaderString("Location");
            String keycloakUserId = locationHeader.substring(locationHeader.lastIndexOf("/") + 1);

            // 4. Assign realm role
            assignRealmRole(keycloakUserId, role);

            return keycloakUserId;

        } else if (response.getStatus() == 409) {
            throw new RuntimeException("Un utilisateur avec ce nom existe déjà dans Keycloak");
        } else {
            throw new RuntimeException("Erreur Keycloak: " + response.getStatus()
                    + " - " + response.readEntity(String.class));
        }
    }

    private void assignRealmRole(String userId, String roleName) {
        RealmResource realmResource = keycloak.realm(realm);
        RolesResource rolesResource = realmResource.roles();
        UserResource userResource = realmResource.users().get(userId);

        try {
            RoleRepresentation role = rolesResource.get(roleName).toRepresentation();
            userResource.roles().realmLevel().add(Collections.singletonList(role));
        } catch (Exception e) {
            // Role doesn't exist in Keycloak — log warning but don't fail
            System.err.println("WARNING: Realm role '" + roleName + "' not found in Keycloak. "
                    + "Make sure it exists in the sageline realm.");
        }
    }

    public void updateUserRole(String username, String newRole) {
        RealmResource realmResource = keycloak.realm(realm);
        UsersResource usersResource = realmResource.users();

        // Find user by username
        List<UserRepresentation> users = usersResource.search(username, true);
        if (users.isEmpty()) {
            throw new RuntimeException("Utilisateur '" + username + "' non trouvé dans Keycloak");
        }

        String userId = users.get(0).getId();
        UserResource userResource = realmResource.users().get(userId);

        // Remove all existing SageLine roles
        List<String> sagelineRoles = List.of(
                "ADMIN_IT", "CHEF_SECTEUR", "EXPERT",
                "TECH_VALIDATION", "TECH_PREPARATION", "RESPONSABLE"
        );

        List<RoleRepresentation> currentRoles = userResource.roles().realmLevel().listAll();
        List<RoleRepresentation> rolesToRemove = currentRoles.stream()
                .filter(r -> sagelineRoles.contains(r.getName()))
                .toList();

        if (!rolesToRemove.isEmpty()) {
            userResource.roles().realmLevel().remove(rolesToRemove);
        }

        // Assign new role
        assignRealmRole(userId, newRole);
    }


    public void updatePassword(String username, String newPassword) {
        RealmResource realmResource = keycloak.realm(realm);
        List<UserRepresentation> users = realmResource.users().search(username, true);

        if (users.isEmpty()) {
            throw new RuntimeException("Utilisateur '" + username + "' non trouvé dans Keycloak");
        }

        String userId = users.get(0).getId();
        UserResource userResource = realmResource.users().get(userId);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);

        userResource.resetPassword(credential);
    }

    public void deleteUser(String username) {
        RealmResource realmResource = keycloak.realm(realm);
        List<UserRepresentation> users = realmResource.users().search(username, true);

        if (!users.isEmpty()) {
            String userId = users.get(0).getId();
            realmResource.users().delete(userId);
        }
    }

   
    public void updateEmail(String username, String newEmail) {
        RealmResource realmResource = keycloak.realm(realm);
        List<UserRepresentation> users = realmResource.users().search(username, true);

        if (users.isEmpty()) {
            throw new RuntimeException("Utilisateur '" + username + "' non trouvé dans Keycloak");
        }

        String userId = users.get(0).getId();
        UserResource userResource = realmResource.users().get(userId);

        UserRepresentation userRep = userResource.toRepresentation();
        userRep.setEmail(newEmail);
        userResource.update(userRep);
    }
}