package stirling.software.proprietary.security.service;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import stirling.software.common.configuration.interfaces.ShowAdminInterface;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.database.repository.UserRepository;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.tenant.TenantContext;

@Service
@RequiredArgsConstructor
class AppUpdateAuthService implements ShowAdminInterface {

    private final UserRepository userRepository;

    private final ApplicationProperties applicationProperties;

    @Override
    public boolean getShowUpdateOnlyAdmins() {
        boolean showUpdate = applicationProperties.getSystem().isShowUpdate();
        if (!showUpdate) {
            return showUpdate;
        }
        boolean showUpdateOnlyAdmin = applicationProperties.getSystem().getShowUpdateOnlyAdmin();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return !showUpdateOnlyAdmin;
        }
        if ("anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return !showUpdateOnlyAdmin;
        }
        Long tenantId = null;
        var descriptor = TenantContext.getTenant();
        if (descriptor != null) {
            tenantId = descriptor.id();
        }
        Optional<User> user =
                tenantId == null
                        ? userRepository.findByUsername(authentication.getName())
                        : userRepository.findByUsernameAndTenantId(
                                authentication.getName(), tenantId);
        if (user.isPresent() && showUpdateOnlyAdmin) {
            return "ROLE_ADMIN".equals(user.get().getRolesAsString());
        }
        return showUpdate;
    }
}
