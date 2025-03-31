package net.oneki.mtac.framework.repository;

import net.oneki.mtac.model.resource.Resource;

public interface ResourceRepositorySync {
    public <T extends Resource> T getByIdUnsecureSync(int id, Class<T> resultContentClass);
    public <T extends Resource> T getByUniqueLabelUnsecureSync(String label,Class<T> resultContentClass);
}
