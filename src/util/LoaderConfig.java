import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LoaderConfig {
    private Properties properties;

    public LoaderConfig(String path){
        properties = new Properties();

        try{
            File file = new File(System.getProperty("user.dir"), path);
            InputStream input = new FileInputStream(file);
            properties.load(input);
        }catch(IOException e){
            System.err.println("Errore recupero file configurazione: "+e.getMessage());
        }

    }

    /* METODI GETTER */
    public String getStringProperty(String key){
        return properties.getProperty(key);
    }

    public int getIntProperty(String key){
        return Integer.parseInt(properties.getProperty(key));
    }

    public long getLongProperty(String key){
        return Long.parseLong(properties.getProperty(key));
    }
}
