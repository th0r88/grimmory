package org.booklore.mapper;

import org.booklore.model.dto.EmailRecipientV2;
import org.booklore.model.dto.request.CreateEmailRecipientRequest;
import org.booklore.model.entity.EmailRecipientV2Entity;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EmailRecipientV2Mapper {

    EmailRecipientV2 toDTO(EmailRecipientV2Entity entity);

    EmailRecipientV2Entity toEntity(EmailRecipientV2 emailRecipient);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    EmailRecipientV2Entity toEntity(CreateEmailRecipientRequest createRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(CreateEmailRecipientRequest request, @MappingTarget EmailRecipientV2Entity entity);
}