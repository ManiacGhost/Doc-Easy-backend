package panscience.chatapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import panscience.chatapp.entity.UploadedAsset;
import panscience.chatapp.entity.User;

import java.util.List;
import java.util.Optional;

public interface UploadedAssetRepository extends JpaRepository<UploadedAsset, Long> {
    List<UploadedAsset> findByUserOrderByUploadedAtDesc(User user);
    Optional<UploadedAsset> findByIdAndUser(Long id, User user);
}

