package com.authentisign.desktop.database.daos.impl;

import com.authentisign.desktop.database.DBConnector;
import com.authentisign.desktop.database.daos.SignatureDao;
import com.authentisign.desktop.database.entities.Signature;
import com.authentisign.desktop.exceptions.DatabaseException;

import java.sql.*;
import java.util.List;
import java.util.Optional;

public class SignatureDaoImpl implements SignatureDao {
    @Override
    public void save(Signature entity) {
        String sql = "INSERT INTO dbo.Signature (DocumentId, UserId, CertificateToken, SigValue, SignedAt) VALUES (?,?,?,?,?)";

        try(Connection conn = DBConnector.getConnection()){

            if(conn == null){
                logger.error("DB Connection is null! Critical error in DBConnector configuration.");
                throw new DatabaseException("A system error occurred. Please try again later.");
            }

            try(PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
                stmt.setLong(1, entity.getDocumentId());
                stmt.setLong(2, entity.getUserId());
                stmt.setString(3, entity.getCertificateToken());
                stmt.setString(4, entity.getSignatureValue());
                stmt.setTimestamp(5, Timestamp.valueOf(entity.getSignedAt()));

                stmt.executeUpdate();

                try(ResultSet rs = stmt.getGeneratedKeys()){
                    if(rs.next()){
                        Long id = rs.getLong(1);
                        entity.setId(id);
                        logger.info("Signature has been saved with ID: {}", id);
                    }
                }
            }

        }catch (SQLException | ClassNotFoundException e){
            logger.error("Failed to save signature to DB. Signature: {}", entity.getId(), e);
            throw new DatabaseException("Signature failed due to a system error. Please try again.");
        }
    }

    @Override
    public void update(Signature entity) {

    }


    @Override
    public Optional<Signature> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public List<Signature> findAll() {
        return List.of();
    }

    //פונקצית עזר
    private Signature mapResultSetToEntity(ResultSet rs) throws SQLException {
        return new Signature(
                rs.getLong("Id"),
                rs.getLong("DocumentId"),
                rs.getLong("UserId"),
                rs.getString("CertificateToken"),
                rs.getString("Format"),
                rs.getString("Value"),
                rs.getTimestamp("SignedAt").toLocalDateTime()
        );
    }
}
