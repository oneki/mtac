package net.oneki.mtac.test.api.resource.tenant.installer;

import org.springframework.stereotype.Service;

import net.oneki.mtac.test.api.resource.tenant.company.CompanyService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InstallerService extends CompanyService<InstallerUpsertRequest, Installer> {

    @Override
    public Installer toCreateEntity(InstallerUpsertRequest request) {
        var company = super.toCreateEntity(request);
        company.setInstaller(true);
        return company;
    }

    @Override
    public Installer toUpdateEntity(String urn, InstallerUpsertRequest request) {
        var company = super.toUpdateEntity(urn, request);
        company.setInstaller(true);
        return company;
    }

    @Override
    public Class<Installer> getEntityClass() {
        return Installer.class;
    }

    @Override
    public Class<InstallerUpsertRequest> getRequestClass() {
        return InstallerUpsertRequest.class;
    }

}
 