-- Synthetic database-only fixture, not evidence of REST business acceptance.
-- This script is invoked only after Flyway in newly created isolated databases.
INSERT INTO administrator_profiles(user_id,full_name,created_at,updated_at,created_by,updated_by)
VALUES ('10000000-0000-0000-0000-000000000002','Audit administrator',now(),now(),'10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001');
INSERT INTO veterinarian_profiles(user_id,full_name,professional_phone_number,professional_registration_number,created_at,updated_at,created_by,updated_by)
VALUES ('10000000-0000-0000-0000-000000000003','Audit veterinarian','+573000000003','AUDIT-FICTIONAL-001',now(),now(),'10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002');
INSERT INTO veterinarian_qualifications(id,veterinarian_id,type,title,institution,base_degree,created_at,updated_at)
VALUES ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000003','UNDERGRADUATE','Fictional audit degree','Fictional audit institution',true,now(),now());
INSERT INTO owner_profiles(user_id,full_name,date_of_birth,phone_number)
VALUES ('10000000-0000-0000-0000-000000000004','Audit owner',DATE '1955-01-01','+573000000004');
INSERT INTO pets(id,owner_id,name,species,sex,active,created_at,updated_at)
VALUES ('30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004','Luna audit fixture','DOG','FEMALE',true,now(),now());
INSERT INTO veterinarian_availability_slots(id,veterinarian_id,starts_at,ends_at,status,created_at,updated_at,created_by,updated_by)
VALUES ('40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000003',date_trunc('day',now())+interval '2 days 14 hours',date_trunc('day',now())+interval '2 days 14 hours 30 minutes','PUBLISHED',now(),now(),'10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002');
INSERT INTO veterinarian_availability_events(id,slot_id,slot_version,actor_id,occurred_at,event_type,new_starts_at,new_ends_at,new_status)
SELECT '40000000-0000-0000-0000-000000000002',id,0,created_by,now(),'CREATED',starts_at,ends_at,status FROM veterinarian_availability_slots;
INSERT INTO appointments(id,owner_id,pet_id,current_slot_id,status,reason,starts_at,ends_at,confirmation_cutoff_at,client_request_id,request_fingerprint,created_at,updated_at)
SELECT '50000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004','30000000-0000-0000-0000-000000000001',id,'REQUESTED','Fictional audit fixture, no clinical purpose',starts_at,ends_at,starts_at-interval '1 hour','50000000-0000-0000-0000-000000000002',repeat('a',64),now(),now() FROM veterinarian_availability_slots;
INSERT INTO appointment_assignments(id,appointment_id,slot_id,veterinarian_id,starts_at,ends_at,assigned_at,actor_id,actor_type)
SELECT '50000000-0000-0000-0000-000000000003','50000000-0000-0000-0000-000000000001',id,veterinarian_id,starts_at,ends_at,now(),'10000000-0000-0000-0000-000000000002','HUMAN' FROM veterinarian_availability_slots;
INSERT INTO appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status,new_assignment_status,new_slot_id,actor_id,actor_type,occurred_at)
VALUES ('50000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000001',0,'REQUESTED',null,'REQUESTED','ASSIGNED','40000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004','HUMAN',now());
UPDATE appointments SET status='CONFIRMED',version=1 WHERE id='50000000-0000-0000-0000-000000000001';
INSERT INTO appointment_events(id,appointment_id,appointment_version,event_type,previous_status,new_status,previous_assignment_status,new_assignment_status,actor_id,actor_type,occurred_at)
VALUES ('50000000-0000-0000-0000-000000000005','50000000-0000-0000-0000-000000000001',1,'CONFIRMED','REQUESTED','CONFIRMED','ASSIGNED','ASSIGNED','10000000-0000-0000-0000-000000000002','HUMAN',now());
INSERT INTO clinical_encounters(id,appointment_id,pet_id,owner_id,veterinarian_id,created_by,reason,created_at,updated_at)
VALUES ('60000000-0000-0000-0000-000000000001','50000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003','Synthetic audit draft; not medical advice',now(),now());
INSERT INTO clinical_encounter_events(id,encounter_id,encounter_version,event_type,actor_id,veterinarian_id,occurred_at)
VALUES ('60000000-0000-0000-0000-000000000002','60000000-0000-0000-0000-000000000001',0,'CREATED','10000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003',now());
