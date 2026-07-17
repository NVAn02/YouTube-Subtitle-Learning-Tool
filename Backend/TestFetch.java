import org.springframework.web.client.RestTemplate;
public class TestFetch {
    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://www.youtube.com/api/timedtext?v=5MuIMqhT8DM&ei=VplZap3-CKuEpt8PlpudgQI&caps=asr&opi=112496729&exp=xpe&xoaf=5&xowf=1&hl=vi&ip=0.0.0.0&ipbits=0&expire=1784282054&sparams=ip,ipbits,expire,v,ei,caps,opi,exp,xoaf&signature=3D023D587A0B5C0126B09FD516A5F4CBC160B390.3BA2C9EC5B2CE6AAFCAAEDA8852F3B2483E73E24&key=yt8&lang=en";
        try {
            String result = restTemplate.getForObject(url, String.class);
            System.out.println("Result length: " + (result != null ? result.length() : "null"));
            System.out.println("Preview: " + (result != null ? result.substring(0, Math.min(200, result.length())) : ""));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
