ALTER TABLE tasks ADD CONSTRAINT fk_tasks_contact FOREIGN KEY(contact_id) REFERENCES contacts(id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_lead FOREIGN KEY(lead_id) REFERENCES leads(id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_project FOREIGN KEY(project_id) REFERENCES projects(id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_lead FOREIGN KEY(lead_id) REFERENCES leads(id);
ALTER TABLE activities ADD CONSTRAINT fk_activities_contact FOREIGN KEY(contact_id) REFERENCES contacts(id);
CREATE INDEX idx_tasks_lead ON tasks(lead_id);
CREATE INDEX idx_tasks_project ON tasks(project_id);
CREATE INDEX idx_activities_lead ON activities(lead_id);

