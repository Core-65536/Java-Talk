package top.c0r3.talk.encrypt;
import java.util.Random;

public class generateEncryptKey implements KeyGenerator{
    @Override
    public String generateKey(String seed) {
        Random rand = new Random(seed.hashCode());
        String key = "";
        for(int i = 0; i < 16; i++){
            switch (rand.nextInt(3)) {
                case 0:
                    char c = (char) (rand.nextInt(26) + 65);
                    key += c;
                    break;
                case 1:
                    char d = (char) (rand.nextInt(26) + 97);
                    key += d;
                    break;
                case 2:
                    char e = (char) (rand.nextInt(10) + 48);
                    key += e;
                    break;
            }
        }
        return key;
    }
}
