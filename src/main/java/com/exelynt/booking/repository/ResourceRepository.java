package com.exelynt.booking.repository;

import com.exelynt.booking.entity.Resource;
import com.exelynt.booking.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findByAvailableTrue();

    List<Resource> findByType(ResourceType type);
}
