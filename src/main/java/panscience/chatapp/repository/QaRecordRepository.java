package panscience.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import panscience.chatapp.entity.QaRecord;

public interface QaRecordRepository extends JpaRepository<QaRecord, Long> {
}

