package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository;
import com.osatum.wbn.domain.model.questionnaire.SurveyJsonConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Czyta kandydatów danego rocznika w jednym zapytaniu, bez ładowania agregatów czasopism.
 * Obejmuje źródła PUBLISHED lub projekcję INDEXED i zachowuje przyczyny wyłączeń zamiast usuwać je z wyniku.
 * Status źródłowy oraz obie wartości ICV pozwalają skontrolować populację faktycznie użytą przez P2.
 */
@Repository
@RequiredArgsConstructor
public class DecisionPopulationRepositoryAdapter implements DecisionPopulationRepository {
    private final JdbcTemplate jdbc;
    private final SurveyJsonConverter converter = new SurveyJsonConverter();

    @Override
    public List<Entry> findCandidates(int year) {
        return jdbc.query("""
                select j.id, q.id as questionnaire_id, r.value_num, s.survey,
                       a.rating_status as assessment_status, a.icv as assessment_icv,
                       case
                         when j.state <> 'ACTIVE' then 'INACTIVE_JOURNAL'
                         when q.state <> 'ACTIVE' then 'INACTIVE_QUESTIONNAIRE'
                         when s.id is null or s.state <> 'ACTIVE' or s.survey is null then 'MISSING_ACTIVE_SURVEY'
                         when (select count(*) from questionnaire d where d.journal_id = j.id
                               and d.questionnaire_year = ? and d.state = 'ACTIVE') <> 1 then 'AMBIGUOUS_QUESTIONNAIRE'
                         when (select count(*) from journal_rating d where d.journal_id = j.id
                               and d.rating_year = ? and d.rating_code = 'ICV' and d.state = 'ACTIVE') <> 1 then 'AMBIGUOUS_ICV_RATING'
                         when r.rating_status is null or r.rating_status <> 'INDEXED' then 'NOT_INDEXED'
                         else null
                       end as exclusion_reason
                from journal j
                join questionnaire q on q.journal_id = j.id and q.questionnaire_year = ?
                left join questionnaire_survey s on s.id = q.survey_data_id
                left join questionnaire_rating_icv a on a.id = q.rating_id
                left join journal_rating r on r.journal_id = j.id and r.rating_year = q.questionnaire_year
                     and r.rating_code = 'ICV' and r.state = 'ACTIVE'
                where a.rating_status = 'PUBLISHED' or r.rating_status = 'INDEXED'
                order by j.id, q.id, r.id
                """, (row, index) -> {
                    String json = row.getString("survey");
                    return new Entry(row.getLong("id"), row.getObject("value_num", Double.class),
                            json == null ? null : converter.convertToEntityAttribute(json),
                            row.getLong("questionnaire_id"), row.getString("assessment_status"),
                            row.getObject("assessment_icv", Double.class), row.getString("exclusion_reason"));
                }, year, year, year);
    }
}
