package net.oneki.mtac.reactive.test.api.resource.tenant.installer;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import net.oneki.mtac.reactive.test.api.resource.tenant.company.CompanyService;

@Service
@RequiredArgsConstructor
public class InstallerService extends CompanyService<InstallerUpsertRequest, Installer> {

    @Override
    public Mono<Installer> toCreateEntity(InstallerUpsertRequest request) {
        var company = super.toCreateEntity(request)
            .doOnNext(c -> {
                c.setInstaller(true);
            });
        return company;
    }

    @Override
    public Mono<Installer> toUpdateEntity(String urn, InstallerUpsertRequest request) {
        var company = super.toUpdateEntity(urn, request)
            .doOnNext(c -> {
                c.setInstaller(true);
            });
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
 