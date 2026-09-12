import com.featureflag.sdk.FeatureFlagClient;
import java.time.Duration;

public class SDKSmoke {
    public static void main(String[] args) {
        var client = FeatureFlagClient.builder()
                .baseUrl(System.getenv("SMOKE_BASE_URL"))
                .sdkKey(System.getenv("SMOKE_SDK_KEY"))
                .connectTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(3)).build();
        boolean enabled = client.isEnabled(System.getenv("SMOKE_FLAG_KEY"), "sdk-subject", false);
        boolean expected = Boolean.parseBoolean(System.getenv("SMOKE_EXPECTED"));
        if (enabled != expected) throw new IllegalStateException("SDK result mismatch");
        System.out.println("SDK_RESULT=" + enabled);
    }
}
