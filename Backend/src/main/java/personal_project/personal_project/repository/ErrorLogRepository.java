package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import personal_project.personal_project.entity.ErrorLog;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>, JpaSpecificationExecutor<ErrorLog> {
}
