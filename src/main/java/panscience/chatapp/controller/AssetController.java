package panscience.chatapp.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import panscience.chatapp.dto.AskRequest;
import panscience.chatapp.dto.AskResponse;
import panscience.chatapp.dto.AssetResponse;
import panscience.chatapp.dto.PlayLinkResponse;
import panscience.chatapp.dto.SummaryResponse;
import panscience.chatapp.dto.TimestampResponse;
import panscience.chatapp.entity.UploadedAsset;
import panscience.chatapp.entity.User;
import panscience.chatapp.service.AssetQaService;
import panscience.chatapp.service.StorageRouterService;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@Validated
public class AssetController {


    private final AssetQaService assetQaService;
    private final StorageRouterService storageRouterService;

    public AssetController(AssetQaService assetQaService, StorageRouterService storageRouterService) {
        this.assetQaService = assetQaService;
        this.storageRouterService = storageRouterService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal User user) {
        return assetQaService.upload(file, user);
    }

    @GetMapping
    public List<AssetResponse> listAssets(@AuthenticationPrincipal User user) {
        return assetQaService.listAssets(user);
    }

    @GetMapping("/{id}")
    public AssetResponse getAsset(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return assetQaService.getAsset(id, user);
    }

    @GetMapping("/{id}/summary")
    public SummaryResponse summarize(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return assetQaService.summarize(id, user);
    }

    @PostMapping("/{id}/ask")
    public AskResponse ask(@PathVariable Long id, @Valid @RequestBody AskRequest request, @AuthenticationPrincipal User user) {
        return assetQaService.ask(id, request.question(), user);
    }

    @GetMapping("/{id}/timestamps")
    public TimestampResponse timestamps(@PathVariable Long id, @RequestParam("topic") @NotBlank String topic, @AuthenticationPrincipal User user) {
        return assetQaService.findTopicTimestamps(id, topic, user);
    }

    @GetMapping("/{id}/play-link")
    public PlayLinkResponse playLink(@PathVariable Long id, @RequestParam(defaultValue = "0") int startSeconds, @AuthenticationPrincipal User user) {
        return assetQaService.buildPlayLink(id, startSeconds, user);
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> streamAsset(@PathVariable Long id, @RequestParam(defaultValue = "0") int startSeconds, @AuthenticationPrincipal User user) {
        UploadedAsset asset = assetQaService.findAsset(id, user);
        String cloudUrl = asset.getCloudUrl();
        
        if (cloudUrl == null || cloudUrl.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // For cloud storage, redirect to the cloud URL with timestamp query param if applicable
        String redirectUrl = cloudUrl;
        if (startSeconds > 0 && asset.getAssetType().toString().equals("VIDEO")) {
            redirectUrl = cloudUrl + "#t=" + startSeconds;
        }
        
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }
}

