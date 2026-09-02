package org.widnees.widCore.migrate;

import java.util.ArrayList;
import java.util.List;

/**
 * Bir migrate işleminin sonucunu tutan basit veri sınıfı.
 */
public class MigrateResult {

    private int success = 0;
    private int failed  = 0;
    private int skipped = 0;
    private final List<String> messages = new ArrayList<>();

    public void addSuccess()          { success++;        }
    public void addFailed()           { failed++;         }
    public void addSkipped()          { skipped++;        }
    public void addMessage(String msg){ messages.add(msg);}

    public int          getSuccess()  { return success;   }
    public int          getFailed()   { return failed;    }
    public int          getSkipped()  { return skipped;   }
    public List<String> getMessages() { return messages;  }
}