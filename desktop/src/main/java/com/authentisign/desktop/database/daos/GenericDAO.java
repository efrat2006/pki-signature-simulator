package com.authentisign.desktop.database.daos;

import com.authentisign.desktop.database.JPAUtil;
import com.authentisign.desktop.exceptions.DatabaseException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public abstract class GenericDAO <T> implements CrudDao<T, Long>{
    Logger logger = LoggerFactory.getLogger(GenericDAO.class);
    private final Class<T> entityClass;

    protected GenericDAO(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    protected EntityManager getEntityManager() {
        return JPAUtil.getEntityManagerFactory().createEntityManager();
    }

    public void save(T entity) {
        EntityManager em = getEntityManager();
        //מניעת זליגת משאבים - מבטיח שהחיבור לSql Server ייסגר תמיד
        try(em){
            em.getTransaction().begin();
            em.persist(entity);
            em.getTransaction().commit();
        } catch(Exception ex){
            if(em.getTransaction().isActive()){
                //שלמות הנתונים
                em.getTransaction().rollback();
            }
            logger.info("Error saving entity "+ ex.getMessage());
            //הגנה מפני חשיפת מידע
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }

    public void update(T entity) {
        EntityManager em = getEntityManager();
        try(em) {
            em.getTransaction().begin();
            T updatedEntity = em.merge(entity);
            em.getTransaction().commit();
        }catch(Exception ex){
            if(em.getTransaction().isActive()){
                em.getTransaction().rollback();
            }
            logger.info("Error updating entity "+ ex.getMessage());
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }

    public Optional<T> findById(Long id) {
        EntityManager em = getEntityManager();
        try(em) {
             T entity = em.find(entityClass, id);
             return Optional.ofNullable(entity);

        } catch(Exception ex){
            if(em.getTransaction().isActive()){
                em.getTransaction().rollback();
            }
            logger.info("Error finding entity "+ ex.getMessage());
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }

    public List<T> findAll() {
        EntityManager em = getEntityManager();
        try(em) {
            String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
            return em.createQuery(jpql, entityClass).getResultList();
        } catch(Exception ex){
            if(em.getTransaction().isActive()){
                em.getTransaction().rollback();
            }
            logger.info("Error finding all entity "+ ex.getMessage());
            throw new DatabaseException("A system error occurred. Please try again later.");
        }
    }
}
