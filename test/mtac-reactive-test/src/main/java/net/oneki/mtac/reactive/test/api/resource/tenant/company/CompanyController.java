package net.oneki.mtac.reactive.test.api.resource.tenant.company;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import net.oneki.mtac.api.ResourceController;

@RestController
public class CompanyController extends ResourceController<CompanyUpsertRequest, Company, DefaultCompanyService> {
    @Autowired protected DefaultCompanyService companyService;
    
    @Override
    protected Class<CompanyUpsertRequest> getRequestClass() {
        return CompanyUpsertRequest.class;
    }

    @Override
    protected Class<Company> getResourceClass() {
        return Company.class;
    }

    @Override
    protected DefaultCompanyService getService() {
        return companyService;
    }
}
