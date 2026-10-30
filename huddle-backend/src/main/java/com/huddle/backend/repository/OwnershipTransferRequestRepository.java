package com.huddle.backend.repository;

import com.huddle.backend.model.OwnershipTransferRequest;
import com.huddle.backend.model.OwnershipTransferRequest.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OwnershipTransferRequestRepository extends JpaRepository<OwnershipTransferRequest, String> {

    List<OwnershipTransferRequest> findByCollaborationIdAndStatus(String collaborationId, TransferStatus status);

    List<OwnershipTransferRequest> findByRecipientIdAndStatus(String recipientId, TransferStatus status);

    Optional<OwnershipTransferRequest> findFirstByCollaborationIdAndStatus(String collaborationId, TransferStatus status);
}
