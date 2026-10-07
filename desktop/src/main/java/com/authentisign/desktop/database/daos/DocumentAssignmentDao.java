package com.authentisign.desktop.database.daos;

import com.authentisign.desktop.database.entities.DocumentAssignment;

public interface DocumentAssignmentDao extends CrudDao<DocumentAssignment, Long>{
    //עדכון בעת חתימה על הקובץ - עדכון השדה הושלם ב...
    void updateCompletionTime(Long assignmentId);
}
