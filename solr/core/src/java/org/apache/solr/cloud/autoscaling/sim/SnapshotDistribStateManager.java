package org.apache.solr.cloud.autoscaling.sim;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.cloud.DistribStateManager;
import org.apache.solr.client.solrj.cloud.autoscaling.AlreadyExistsException;
import org.apache.solr.client.solrj.cloud.autoscaling.AutoScalingConfig;
import org.apache.solr.client.solrj.cloud.autoscaling.BadVersionException;
import org.apache.solr.client.solrj.cloud.autoscaling.NotEmptyException;
import org.apache.solr.client.solrj.cloud.autoscaling.VersionedData;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.params.AutoScalingParams;
import org.apache.solr.common.util.Base64;
import org.apache.solr.common.util.Utils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.Watcher;

/**
 * Read-only snapshot of another {@link DistribStateManager}
 */
public class SnapshotDistribStateManager implements DistribStateManager {

  LinkedHashMap<String, VersionedData> dataMap = new LinkedHashMap<>();

  public SnapshotDistribStateManager(DistribStateManager other) throws Exception {
    List<String> tree = other.listTree("/");
    for (String path : tree) {
      dataMap.put(path, other.getData(path));
    }
  }

  public SnapshotDistribStateManager(Map<String, Object> snapshot) {
    snapshot.forEach((path, value) -> {
      Map<String, Object> map = (Map<String, Object>)value;
      int version = (Integer)map.getOrDefault("version", 0);
      String owner = (String)map.get("owner");
      String modeStr = (String)map.getOrDefault("mode", CreateMode.PERSISTENT.toString());
      CreateMode mode = CreateMode.valueOf(modeStr);
      byte[] bytes = null;
      if (map.containsKey("data")) {
        bytes = Base64.base64ToByteArray((String)map.get("data"));
      }
      dataMap.put(path, new VersionedData(version, bytes, mode, owner));
    });
  }

  public Map<String, Object> getSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    dataMap.forEach((path, vd) -> {
      Map<String, Object> data = new HashMap<>();
      vd.toMap(data);
      snapshot.put(path, data);
    });
    return snapshot;
  }

  @Override
  public boolean hasData(String path) throws IOException, KeeperException, InterruptedException {
    return dataMap.containsKey(path);
  }

  @Override
  public List<String> listData(String path) throws NoSuchElementException, IOException, KeeperException, InterruptedException {
    return listData(path, null);
  }

  @Override
  public List<String> listData(String path, Watcher watcher) throws NoSuchElementException, IOException, KeeperException, InterruptedException {
    final String prefix = path + "/";
    return dataMap.entrySet().stream()
        .filter(e -> e.getKey().startsWith(prefix))
        .map(e -> {
          String suffix = e.getKey().substring(prefix.length());
          int idx = suffix.indexOf('/');
          if (idx == -1) {
            return suffix;
          } else {
            return suffix.substring(0, idx);
          }
        })
        .collect(Collectors.toList());
  }

  @Override
  public VersionedData getData(String path, Watcher watcher) throws NoSuchElementException, IOException, KeeperException, InterruptedException {
    if (!dataMap.containsKey(path)) {
      throw new NoSuchElementException(path);
    }
    return dataMap.get(path);
  }

  @Override
  public void makePath(String path) throws AlreadyExistsException, IOException, KeeperException, InterruptedException {
    throw new UnsupportedOperationException("makePath");
  }

  @Override
  public void makePath(String path, byte[] data, CreateMode createMode, boolean failOnExists) throws AlreadyExistsException, IOException, KeeperException, InterruptedException {
    throw new UnsupportedOperationException("makePath");
  }

  @Override
  public String createData(String path, byte[] data, CreateMode mode) throws AlreadyExistsException, IOException, KeeperException, InterruptedException {
    throw new UnsupportedOperationException("createData");
  }

  @Override
  public void removeData(String path, int version) throws NoSuchElementException, IOException, NotEmptyException, KeeperException, InterruptedException, BadVersionException {
    throw new UnsupportedOperationException("removeData");
  }

  @Override
  public void setData(String path, byte[] data, int version) throws BadVersionException, NoSuchElementException, IOException, KeeperException, InterruptedException {
    throw new UnsupportedOperationException("setData");
  }

  @Override
  public List<OpResult> multi(Iterable<Op> ops) throws BadVersionException, NoSuchElementException, AlreadyExistsException, IOException, KeeperException, InterruptedException {
    throw new UnsupportedOperationException("multi");
  }

  @Override
  public AutoScalingConfig getAutoScalingConfig(Watcher watcher) throws InterruptedException, IOException {
    VersionedData vd = dataMap.get(ZkStateReader.SOLR_AUTOSCALING_CONF_PATH);
    Map<String, Object> map = new HashMap<>();
    if (vd != null && vd.getData() != null && vd.getData().length > 0) {
      map = (Map<String, Object>) Utils.fromJSON(vd.getData());
      map.put(AutoScalingParams.ZK_VERSION, vd.getVersion());
    }
    return new AutoScalingConfig(map);
  }

  @Override
  public void close() throws IOException {

  }
}
