package net.oneki.mtac.core.repository.framework;

import org.springframework.beans.factory.annotation.Autowired;

import net.oneki.mtac.core.repository.ResourceRepository;
import net.oneki.mtac.model.resource.Resource;
import reactor.core.publisher.Mono;

public abstract class BaseRepository<T extends Resource> extends AbstractRepository {
    protected ResourceRepository resourceRepository;

    @Autowired
    public void setResourceRepository(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    // Create
    public Mono<T> create(T userEntity) {
        return resourceRepository.create(userEntity);
    }

    // update user
    public Mono<T> update(T userEntity) {
        return resourceRepository.update(userEntity).then(Mono.just(userEntity));
    }

    // delete user
    public Mono<Void> delete(Integer id) {
        return resourceRepository.delete(id);
    }

    public Mono<Void> delete(String tenantLabel, String label) {
        return resourceRepository.delete(tenantLabel, label, getResourceContentClass());
    }

    public abstract Class<T> getResourceContentClass();
}
