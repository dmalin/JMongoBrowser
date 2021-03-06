/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mongo.jmongob;

import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.swingfast.XmlUnit;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.mongo.jmongob.ReplSetPanel.Item;

/**
 *
 * @author antoine
 */
public class ReplSetPanel extends BasePanel implements EnumListener<Item> {

    enum Item {
        refresh,
        name,
        replicaSetStatus,
        oplogInfo,
        maxObjectSize,
        compareReplicas,
        crStat
    }

    public ReplSetPanel() {
        setEnumBinding(Item.values(), this);
    }

    public ReplSetNode getReplSetNode() {
        return (ReplSetNode) node;
    }

    @Override
    protected void updateComponentCustom(JPanel comp) {
        try {
            setStringFieldValue(Item.name, getReplSetNode().getName());
            setStringFieldValue(Item.maxObjectSize, String.valueOf(getReplSetNode().getMongo().getReplicaSetStatus().getMaxBsonObjectSize()));
        } catch (Exception e) {
            JMongoBrowser.instance.showError(this.getClass().getSimpleName() + " update", e);
        }
    }

    @Override
    public void actionPerformed(Item enm, XmlComponentUnit unit, Object src) {
    }

    public void replicaSetStatus() {
        new DocView(null, "RS Status", getReplSetNode().getMongo().getDB("admin"), "replSetGetStatus").addToTabbedDiv();
    }
    
    public void oplogInfo() {
        new DocView(null, "Oplog Info", MongoUtils.getReplicaSetInfo(getReplSetNode().getMongo()),  "Oplog of " + getReplSetNode().getMongo().toString(), null).addToTabbedDiv();
    }

    public void compareReplicas() {
        final String stat = getStringFieldValue(Item.crStat);
        new DbJob() {

            @Override
            public Object doRun() {
                ReplSetNode node = getReplSetNode();
                if (!node.hasChildren())
                    return null;

                ArrayList<Mongo> svrs = new ArrayList<Mongo>();
                for (XmlUnit unit : node.getChildren()) {
                    ServerNode svr = (ServerNode) unit;
                    Mongo svrm = svr.getServerMongo();
                    try {
                        svrm.getDatabaseNames();
                    } catch (Exception e) {
                        continue;
                    }
                    svrs.add(svrm);
                }

                BasicDBObject res = new BasicDBObject();
                Mongo m = getReplSetNode().getMongo();
                for (String dbname : m.getDatabaseNames()) {
                    DB db = m.getDB(dbname);
                    BasicDBObject dbres = new BasicDBObject();
                    for (String colname: db.getCollectionNames()) {
                        DBCollection col = db.getCollection(colname);
                        BasicDBObject colres = new BasicDBObject();
                        BasicDBObject values = new BasicDBObject();
                        boolean same = true;
                        long ref = -1;
                        for (Mongo svrm : svrs) {
                            DBCollection svrcol = svrm.getDB(dbname).getCollection(colname);
                            long value = 0;
                            if (stat.startsWith("Count")) {
                                value = svrcol.count();
                            } else if (stat.startsWith("Data Size")) {
                                CommandResult stats = svrcol.getStats();
                                value = stats.getLong("size");
                            }
                            values.append(svrm.getConnectPoint(), value);
                            if (ref < 0)
                                ref = value;
                            else if (ref != value)
                                same = false;
                        }
                        if (!same) {
                            colres.append("values", values);
                            dbres.append(colname, colres);
                        }
                    }
                    if (!dbres.isEmpty()) {
                        res.append(dbname, dbres);
                    }
                }

                return res;
            }

            @Override
            public String getNS() {
                return "*";
            }

            @Override
            public String getShortName() {
                return "Compare Replicas";
            }
        }.addJob();
    }

}
