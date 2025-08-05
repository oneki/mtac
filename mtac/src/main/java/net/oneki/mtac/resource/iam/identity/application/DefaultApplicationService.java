package net.oneki.mtac.resource.iam.identity.application;

import org.springframework.stereotype.Component;

import net.oneki.mtac.model.resource.iam.identity.application.Application;
import net.oneki.mtac.model.resource.iam.identity.application.ApplicationUpsertRequest;

@Component
public class DefaultApplicationService extends ApplicationService<ApplicationUpsertRequest, Application> {

    @Override
    public Class<Application> getEntityClass() {
        return Application.class;
    }

    @Override
    public Class<ApplicationUpsertRequest> getRequestClass() {
        return ApplicationUpsertRequest.class;
    }

    
}
