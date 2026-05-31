package com.superchat.moderation.repo;

import com.superchat.moderation.domain.WordList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WordListRepository extends JpaRepository<WordList, UUID> {
    List<WordList> findByOrgId(UUID orgId);
}
