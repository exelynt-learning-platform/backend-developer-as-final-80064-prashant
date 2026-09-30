package com.exelynt.booking.service;

import com.exelynt.booking.dto.ResourceRequest;
import com.exelynt.booking.dto.ResourceResponse;
import com.exelynt.booking.entity.Resource;
import com.exelynt.booking.exception.ResourceNotFoundException;
import com.exelynt.booking.repository.ResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public ResourceService(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ResourceResponse createResource(ResourceRequest request) {
        Resource resource = new Resource(
                request.getName(),
                request.getType(),
                request.getDescription(),
                request.getLocation(),
                request.getCapacity(),
                request.getBasePrice(),
                request.getAvailable() != null ? request.getAvailable() : true
        );

        Resource saved = resourceRepository.save(resource);
        return ResourceResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public ResourceResponse getResourceById(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
        return ResourceResponse.fromEntity(resource);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> getAllResources(Boolean availableOnly) {
        List<Resource> resources;
        if (Boolean.TRUE.equals(availableOnly)) {
            resources = resourceRepository.findByAvailableTrue();
        } else {
            resources = resourceRepository.findAll();
        }

        return resources.stream()
                .map(ResourceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResourceResponse updateResource(Long id, ResourceRequest request) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));

        resource.setName(request.getName());
        resource.setType(request.getType());
        resource.setDescription(request.getDescription());
        resource.setLocation(request.getLocation());
        resource.setCapacity(request.getCapacity());
        resource.setBasePrice(request.getBasePrice());
        if (request.getAvailable() != null) {
            resource.setAvailable(request.getAvailable());
        }

        Resource updated = resourceRepository.save(resource);
        return ResourceResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteResource(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resource not found with id: " + id);
        }
        resourceRepository.deleteById(id);
    }
}
