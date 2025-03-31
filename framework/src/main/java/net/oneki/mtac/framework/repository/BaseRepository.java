package net.oneki.mtac.framework.repository;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.model.resource.Resource;

public abstract class BaseRepository<T extends Resource> extends AbstractRepository {
    protected ResourceRepository resourceRepository;

    @Autowired
    public void setResourceRepository(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    // Create
    public T create(T userEntity) {
        return resourceRepository.create(userEntity);
    }

    // update user
    public T update(T userEntity) {
        resourceRepository.update(userEntity);
        return userEntity;
    }

    // delete user
    public void delete(Integer id) {
        resourceRepository.delete(id);
    }

    public void delete(String tenantLabel, String label) {
        resourceRepository.delete(tenantLabel, label, getResourceContentClass());
    }

    public abstract Class<T> getResourceContentClass();
}
