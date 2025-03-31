package net.oneki.mtac.test.api.resource.tenant.company;

import org.springframework.stereotype.Service;

@Service
public class DefaultCompanyService extends CompanyService<CompanyUpsertRequest, Company> {

    @Override
    public Class<Company> getEntityClass() {
        return Company.class;
    }

    @Override
    public Class<CompanyUpsertRequest> getRequestClass() {
        return CompanyUpsertRequest.class;
    }
    
}
