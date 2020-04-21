package ai.verta.modeldb.lineage;

import static ai.verta.modeldb.entities.lineage.ConnectionEntity.CONNECTION_TYPE_ANY;
import static ai.verta.modeldb.entities.lineage.ConnectionEntity.CONNECTION_TYPE_INPUT;
import static ai.verta.modeldb.entities.lineage.ConnectionEntity.CONNECTION_TYPE_OUTPUT;
import static ai.verta.modeldb.entities.lineage.ConnectionEntity.ENTITY_TYPE_EXPERIMENT_RUN;
import static ai.verta.modeldb.entities.lineage.ConnectionEntity.ENTITY_TYPE_VERSIONING_BLOB;

import ai.verta.modeldb.AddLineage;
import ai.verta.modeldb.AddLineage.Response;
import ai.verta.modeldb.DeleteLineage;
import ai.verta.modeldb.FindAllInputs;
import ai.verta.modeldb.FindAllInputsOutputs;
import ai.verta.modeldb.FindAllOutputs;
import ai.verta.modeldb.LineageEntry;
import ai.verta.modeldb.LineageEntryBatchRequest;
import ai.verta.modeldb.LineageEntryBatchResponse;
import ai.verta.modeldb.LineageEntryBatchResponseSingle;
import ai.verta.modeldb.Location;
import ai.verta.modeldb.ModelDBException;
import ai.verta.modeldb.VersioningLineageEntry;
import ai.verta.modeldb.entities.lineage.ConnectionEntity;
import ai.verta.modeldb.entities.lineage.LineageElementEntity;
import ai.verta.modeldb.entities.lineage.LineageExperimentRunEntity;
import ai.verta.modeldb.entities.lineage.LineageVersioningBlobEntity;
import ai.verta.modeldb.utils.ModelDBHibernateUtil;
import ai.verta.modeldb.utils.ModelDBUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status.Code;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;

public class LineageDAORdbImpl implements LineageDAO {

  private static final Logger LOGGER = LogManager.getLogger(LineageDAORdbImpl.class);

  public LineageDAORdbImpl() {}

  @Override
  public Response addLineage(AddLineage addLineage, ExistsCheckConsumer existsCheckConsumer)
      throws ModelDBException, InvalidProtocolBufferException, NoSuchAlgorithmException {
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      session.beginTransaction();
      Long id;
      LineageElementEntity lineageElementEntity;
      if (addLineage.getId() != 0) {
        id = addLineage.getId();
        lineageElementEntity = session.get(LineageElementEntity.class, addLineage.getId());
      } else {
        id = null;
        lineageElementEntity = null;
      }
      if (lineageElementEntity == null) {
        lineageElementEntity = new LineageElementEntity(id);
        session.save(lineageElementEntity);
        id = lineageElementEntity.getId();
      }
      validate(addLineage.getInputList(), addLineage.getOutputList());
      List<LineageEntry> lineageEntries = new LinkedList<>(addLineage.getInputList());
      lineageEntries.addAll(addLineage.getOutputList());
      existsCheckConsumer.test(session, lineageEntries);
      for (LineageEntry input : addLineage.getInputList()) {
        addLineage(session, input, id, CONNECTION_TYPE_INPUT);
      }
      for (LineageEntry output : addLineage.getOutputList()) {
        addLineage(session, output, id, CONNECTION_TYPE_OUTPUT);
      }
      session.getTransaction().commit();
      return AddLineage.Response.newBuilder().setId(lineageElementEntity.getId()).build();
    }
  }

  @Override
  public DeleteLineage.Response deleteLineage(DeleteLineage deleteLineage)
      throws ModelDBException, InvalidProtocolBufferException {
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      session.beginTransaction();
      validate(deleteLineage.getInputList(), deleteLineage.getOutputList());
      long id = deleteLineage.getId();
      if (id == 0) {
        throw new ModelDBException("Id not specified", Code.INVALID_ARGUMENT);
      }
      List connectionEntitiesById = getConnectionEntitiesById(session, id);
      Map.Entry<Map<LineageElement, ConnectionEntity>, Map<LineageElement, ConnectionEntity>>
          inputOutputs = getInputOutputs(session, connectionEntitiesById);
      Map<LineageElement, ConnectionEntity> inputInDatabase = inputOutputs.getKey();
      Map<LineageElement, ConnectionEntity> outputInDatabase = inputOutputs.getValue();
      for (LineageEntry input : deleteLineage.getInputList()) {
        deleteLineage(session, input, inputInDatabase);
      }
      for (LineageEntry output : deleteLineage.getOutputList()) {
        deleteLineage(session, output, outputInDatabase);
      }
      if (inputInDatabase.isEmpty() || outputInDatabase.isEmpty()) {
        LineageElementEntity lineageElementEntity = session.get(LineageElementEntity.class, id);
        session.remove(lineageElementEntity);
      }
      session.getTransaction().commit();
    }
    return DeleteLineage.Response.newBuilder().setStatus(true).build();
  }

  private Entry<Map<LineageElement, ConnectionEntity>, Map<LineageElement, ConnectionEntity>>
      getInputOutputs(Session session, List result) throws ModelDBException {
    Map<LineageElement, ConnectionEntity> inputInDatabase = new HashMap<>();
    Map<LineageElement, ConnectionEntity> outputInDatabase = new HashMap<>();
    for (Object entity : result) {
      ConnectionEntity connectionEntity = (ConnectionEntity) entity;
      if (connectionEntity.getConnectionType() == CONNECTION_TYPE_INPUT) {
        inputInDatabase.put(connectionEntity.getLineageElement(session), connectionEntity);
      } else {
        outputInDatabase.put(connectionEntity.getLineageElement(session), connectionEntity);
      }
    }
    return new AbstractMap.SimpleEntry<>(inputInDatabase, outputInDatabase);
  }

  private Map<LineageElement, ConnectionEntity> getInputOrOutput(Session session, List result)
      throws ModelDBException {
    Map<LineageElement, ConnectionEntity> inputOrOutputInDatabase = new HashMap<>();
    for (Object entity : result) {
      ConnectionEntity connectionEntity = (ConnectionEntity) entity;
      inputOrOutputInDatabase.put(connectionEntity.getLineageElement(session), connectionEntity);
    }
    return inputOrOutputInDatabase;
  }

  private List getConnectionEntitiesById(Session session, long id) {
    return getConnectionEntitiesByIdAndConnectionType(session, id, CONNECTION_TYPE_ANY);
  }

  private List getConnectionEntitiesByIdAndConnectionType(
      Session session, long id, int connectionType) {
    String queryString = "from " + ConnectionEntity.class.getSimpleName() + " where id = " + id;
    if (connectionType != CONNECTION_TYPE_ANY) {
      queryString += " and connectionType = " + connectionType;
    }
    Query query = session.createQuery(queryString);
    return query.list();
  }

  private Map<Long, List<LineageEntry>> getConnectionEntitiesByEntryAndConnectionType(
      Session session, LineageEntry lineageEntry, int connectionType)
      throws ModelDBException, InvalidProtocolBufferException {
    String queryString;
    List<Long> elements = new LinkedList<>();
    int entityType;
    switch (lineageEntry.getDescriptionCase()) {
      case EXPERIMENT_RUN:
        queryString =
            "from "
                + LineageExperimentRunEntity.class.getSimpleName()
                + " where experimentRunId = '"
                + lineageEntry.getExperimentRun()
                + "'";
        Query query = session.createQuery(queryString);
        List experimentRunEntityList = query.list();
        for (Object entity : experimentRunEntityList) {
          LineageExperimentRunEntity lineageExperimentRunEntity =
              (LineageExperimentRunEntity) entity;
          elements.add(lineageExperimentRunEntity.getId());
        }
        entityType = ENTITY_TYPE_EXPERIMENT_RUN;
        break;
      case BLOB:
        VersioningLineageEntry blob = lineageEntry.getBlob();
        queryString =
            "from "
                + LineageVersioningBlobEntity.class.getSimpleName()
                + " where repositoryId = '"
                + blob.getRepositoryId()
                + "' and commitSha = '"
                + blob.getCommitSha()
                + "' and location = '"
                + ModelDBUtils.getStringFromProtoObject(
                    Location.newBuilder().addAllLocation(blob.getLocationList()))
                + "'";
        List versioningBlobEntityList = session.createQuery(queryString).list();
        for (Object entity : versioningBlobEntityList) {
          LineageVersioningBlobEntity lineageVersioningBlobEntity =
              (LineageVersioningBlobEntity) entity;
          elements.add(lineageVersioningBlobEntity.getId());
        }
        entityType = ENTITY_TYPE_VERSIONING_BLOB;
        break;
      default:
        throw new ModelDBException("Unknown entry type");
    }
    Map<Long, List<LineageEntry>> result = new HashMap<>();
    for (Long entry : elements) {
      queryString =
          "from "
              + ConnectionEntity.class.getSimpleName()
              + " where entityId = "
              + entry
              + " and entity_type = "
              + entityType
              + " and connectionType = "
              + invert(connectionType);
      Query query = session.createQuery(queryString);
      List list = query.list();
      for (Object entity : list) {
        ConnectionEntity connectionEntity = (ConnectionEntity) entity;
        List connectionEntitiesById =
            getConnectionEntitiesByIdAndConnectionType(
                session, connectionEntity.getId(), connectionType);
        Map<LineageElement, ConnectionEntity> inputOrOutputInDatabase =
            getInputOrOutput(session, connectionEntitiesById);
        result.put(
            connectionEntity.getId(),
            inputOrOutputInDatabase.keySet().stream()
                .map(LineageElement::toProto)
                .collect(Collectors.toList()));
      }
    }
    return result;
  }

  private int invert(int connectionType) throws ModelDBException {
    switch (connectionType) {
      case CONNECTION_TYPE_INPUT:
        return CONNECTION_TYPE_OUTPUT;
      case CONNECTION_TYPE_OUTPUT:
        return CONNECTION_TYPE_INPUT;
      default:
        throw new ModelDBException("Unknown connection type");
    }
  }

  private void validate(List<LineageEntry> inputList, List<LineageEntry> outputList)
      throws ModelDBException {
    validate(inputList);
    validate(outputList);
  }

  private void validate(List<LineageEntry> list) throws ModelDBException {
    Set<LineageEntry> ids = new HashSet<>();
    for (LineageEntry input : list) {
      ids.add(input);
      validate(input);
    }
    if (ids.size() != list.size()) {
      throw new ModelDBException("Non-unique resource ids in a requests", Code.INVALID_ARGUMENT);
    }
  }

  private void validate(LineageEntryBatchRequest lineageEntryBatchRequest) throws ModelDBException {
    final String message;
    switch (lineageEntryBatchRequest.getIdentifierCase()) {
      case ENTRY:
        LineageEntry lineageEntry = lineageEntryBatchRequest.getEntry();
        validate(lineageEntry);
        message = null;
        break;
      case ID:
        message = null;
        break;
      default:
        message = "Unknown request type";
    }
    if (message != null) {
      LOGGER.warn(message);
      throw new ModelDBException(message, Code.INVALID_ARGUMENT);
    }
  }

  private void validate(LineageEntry lineageEntry) throws ModelDBException {
    final String message;
    switch (lineageEntry.getDescriptionCase()) {
      case BLOB:
        VersioningLineageEntry blob = lineageEntry.getBlob();
        if (blob.getCommitSha().isEmpty()) {
          message = "Commit sha is empty";
        } else if (blob.getLocationCount() == 0) {
          message = "Location is empty";
        } else {
          message = null;
        }
        break;
      case EXPERIMENT_RUN:
        if (lineageEntry.getExperimentRun().isEmpty()) {
          message = "Experiment run id is empty";
        } else {
          message = null;
        }
        break;
      default:
        message = "Unknown lineage type";
    }
    if (message != null) {
      LOGGER.warn(message);
      throw new ModelDBException(message, Code.INVALID_ARGUMENT);
    }
  }

  @Override
  public FindAllInputs.Response findAllInputs(FindAllInputs findAllInputs)
      throws ModelDBException, InvalidProtocolBufferException {
    FindAllInputs.Response.Builder response = FindAllInputs.Response.newBuilder();
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      List<LineageEntryBatchRequest> itemList = findAllInputs.getItemsList();
      for (LineageEntryBatchRequest output : itemList) {
        validate(output);
        response.addInputs(
            LineageEntryBatchResponse.newBuilder()
                .addAllItems(getInputsByOutput(session, output))
                .build());
      }
    }
    return response.build();
  }

  @Override
  public FindAllOutputs.Response findAllOutputs(FindAllOutputs findAllOutputs)
      throws ModelDBException, InvalidProtocolBufferException {
    FindAllOutputs.Response.Builder response = FindAllOutputs.Response.newBuilder();
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      List<LineageEntryBatchRequest> itemList = findAllOutputs.getItemsList();
      for (LineageEntryBatchRequest input : itemList) {
        validate(input);
        response.addOutputs(
            LineageEntryBatchResponse.newBuilder()
                .addAllItems(getOutputsByInput(session, input))
                .build());
      }
    }
    return response.build();
  }

  @Override
  public FindAllInputsOutputs.Response findAllInputsOutputs(
      FindAllInputsOutputs findAllInputsOutputs)
      throws ModelDBException, InvalidProtocolBufferException {
    FindAllInputsOutputs.Response.Builder response = FindAllInputsOutputs.Response.newBuilder();
    try (Session session = ModelDBHibernateUtil.getSessionFactory().openSession()) {
      List<LineageEntryBatchRequest> itemList = findAllInputsOutputs.getItemsList();
      for (LineageEntryBatchRequest inputoutput : itemList) {
        validate(inputoutput);
        response
            .addInputs(
                LineageEntryBatchResponse.newBuilder()
                    .addAllItems(getInputsByOutput(session, inputoutput))
                    .build())
            .addOutputs(
                LineageEntryBatchResponse.newBuilder()
                    .addAllItems(getOutputsByInput(session, inputoutput))
                    .build());
      }
    }
    return response.build();
  }

  private void deleteLineage(
      Session session, LineageEntry lineageEntry, Map<LineageElement, ConnectionEntity> entityIds)
      throws InvalidProtocolBufferException, ModelDBException {
    LineageElement key = LineageElement.fromProto(lineageEntry);
    ConnectionEntity connectionEntity = entityIds.get(key);
    if (connectionEntity != null) {
      switch (connectionEntity.getEntityType()) {
        case ENTITY_TYPE_EXPERIMENT_RUN:
          session.delete(
              session.get(LineageExperimentRunEntity.class, connectionEntity.getEntityId()));
          break;
        case ENTITY_TYPE_VERSIONING_BLOB:
          session.delete(
              session.get(LineageVersioningBlobEntity.class, connectionEntity.getEntityId()));
          break;
        default:
          throw new ModelDBException("Unknown connection type");
      }
      session.delete(connectionEntity);
      entityIds.keySet().remove(key);
    }
  }

  private void addLineage(Session session, LineageEntry lineageEntry, Long id, int connectionType)
      throws ModelDBException, InvalidProtocolBufferException {
    Long entityId;
    int entityType;
    switch (lineageEntry.getDescriptionCase()) {
      case EXPERIMENT_RUN:
        LineageExperimentRunEntity experimentRun =
            new LineageExperimentRunEntity(lineageEntry.getExperimentRun());
        session.saveOrUpdate(experimentRun);
        entityId = experimentRun.getId();
        entityType = ENTITY_TYPE_EXPERIMENT_RUN;
        break;
      case BLOB:
        VersioningLineageEntry blob = lineageEntry.getBlob();
        LineageVersioningBlobEntity lineageVersioningBlobEntity =
            new LineageVersioningBlobEntity(
                blob.getRepositoryId(),
                blob.getCommitSha(),
                ModelDBUtils.getStringFromProtoObject(
                    Location.newBuilder().addAllLocation(blob.getLocationList())));
        session.saveOrUpdate(lineageVersioningBlobEntity);
        entityId = lineageVersioningBlobEntity.getId();
        entityType = ENTITY_TYPE_VERSIONING_BLOB;
        break;
      default:
        throw new ModelDBException("Unknown lineage type");
    }
    session.saveOrUpdate(new ConnectionEntity(id, entityId, connectionType, entityType));
  }

  private List<LineageEntryBatchResponseSingle> getOutputsByInput(
      Session session, LineageEntryBatchRequest input)
      throws InvalidProtocolBufferException, ModelDBException {
    return getInputOrOutput(session, input, CONNECTION_TYPE_OUTPUT);
  }

  private List<LineageEntryBatchResponseSingle> getInputsByOutput(
      Session session, LineageEntryBatchRequest output)
      throws InvalidProtocolBufferException, ModelDBException {
    return getInputOrOutput(session, output, CONNECTION_TYPE_INPUT);
  }

  private List<LineageEntryBatchResponseSingle> getInputOrOutput(
      Session session, LineageEntryBatchRequest sideB, int connectionType)
      throws InvalidProtocolBufferException, ModelDBException {
    Map<Long, List<LineageEntry>> sideAInDatabase;
    switch (sideB.getIdentifierCase()) {
      case ID:
        List connectionEntitiesById =
            getConnectionEntitiesByIdAndConnectionType(session, sideB.getId(), connectionType);
        sideAInDatabase =
            Collections.singletonMap(
                sideB.getId(),
                getInputOrOutput(session, connectionEntitiesById).keySet().stream()
                    .map(LineageElement::toProto)
                    .collect(Collectors.toList()));
        break;
      case ENTRY:
        sideAInDatabase =
            getConnectionEntitiesByEntryAndConnectionType(
                session, sideB.getEntry(), connectionType);
        break;
      default:
        throw new ModelDBException("Unknown id type");
    }
    List<LineageEntryBatchResponseSingle> result = new LinkedList<>();
    for (Map.Entry<Long, List<LineageEntry>> entry : sideAInDatabase.entrySet()) {
      result.add(
          LineageEntryBatchResponseSingle.newBuilder()
              .setId(entry.getKey())
              .addAllItems(entry.getValue())
              .build());
    }
    return result;
  }
}
