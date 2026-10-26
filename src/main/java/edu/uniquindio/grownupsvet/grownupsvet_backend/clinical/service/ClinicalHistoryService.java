package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.service;

import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalHistoryEntryResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.dto.ClinicalHistoryPageResponseDTO;
import edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception.ClinicalOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClinicalHistoryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ClinicalEncounterService encounters;

    public ClinicalHistoryService(NamedParameterJdbcTemplate jdbc, ClinicalEncounterService encounters) {
        this.jdbc = jdbc;
        this.encounters = encounters;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClinicalHistoryPageResponseDTO list(UUID actorId, UUID petId, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ClinicalOperationException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La paginación solicitada no es válida.");
        }
        var reader = encounters.requirePublishedHistoryReader(actorId, petId);
        String predicate = "e.pet_id=:pet and " + encounters.publishedReadPredicate(reader);
        MapSqlParameterSource parameters = new MapSqlParameterSource().addValue("actor", actorId).addValue("pet", petId)
                .addValue("limit", size).addValue("offset", (long) page * size);
        long totalElements = jdbc.queryForObject("select count(*) from clinical_encounters e where " + predicate, parameters, Long.class);
        List<ClinicalHistoryEntryResponseDTO> entries = jdbc.query(
                "select e.id,e.appointment_id,e.pet_id,e.veterinarian_id,v.full_name,e.reason,e.finalized_at " +
                        "from clinical_encounters e join veterinarian_profiles v on v.user_id=e.veterinarian_id " +
                        "where " + predicate + " order by e.finalized_at desc,e.id desc limit :limit offset :offset", parameters,
                (resultSet, rowNumber) -> new ClinicalHistoryEntryResponseDTO(resultSet.getObject("id", UUID.class),
                        resultSet.getObject("appointment_id", UUID.class), resultSet.getObject("pet_id", UUID.class),
                        resultSet.getObject("veterinarian_id", UUID.class), resultSet.getString("full_name"),
                        resultSet.getString("reason"), resultSet.getTimestamp("finalized_at").toInstant()));
        return new ClinicalHistoryPageResponseDTO(entries, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
    }
}
