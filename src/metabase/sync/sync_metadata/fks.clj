(ns metabase.sync.sync-metadata.fks
  "Logic for updating FK properties of Fields from metadata fetched from a physical DB."
  (:require
   [metabase.models.field :refer [Field]]
   [metabase.models.table :as table :refer [Table]]
   [metabase.sync.fetch-metadata :as fetch-metadata]
   [metabase.sync.interface :as i]
   [metabase.sync.util :as sync-util]
   [metabase.util :as u]
   [metabase.util.log :as log]
   [metabase.util.malli :as mu]
   [toucan2.core :as t2]))

(def ^:private FKRelationshipObjects
  "Relevant objects for a foreign key relationship."
  [:map
   [:source-field i/FieldInstance]
   [:dest-table   i/TableInstance]
   [:dest-field   i/FieldInstance]])

(mu/defn ^:private fetch-fk-relationship-objects :- [:maybe FKRelationshipObjects]
  "Fetch the Metabase objects (Tables and Fields) that are relevant to a foreign key relationship described by FK."
  [database :- i/DatabaseInstance
   table    :- i/TableInstance
   fk       :- i/FKMetadataEntry]
  (when-let [source-field (t2/select-one Field
                            :table_id           (u/the-id table)
                            :%lower.name        (u/lower-case-en (:fk-column-name fk))
                            :fk_target_field_id nil
                            :active             true
                            :visibility_type    [:not= "retired"])]
    (when-let [dest-table (t2/select-one Table
                            :db_id           (u/the-id database)
                            :%lower.name     (u/lower-case-en (-> fk :dest-table :name))
                            :%lower.schema   (when-let [schema (-> fk :dest-table :schema)]
                                               (u/lower-case-en schema))
                            :active          true
                            :visibility_type nil)]
      (when-let [dest-field (t2/select-one Field
                              :table_id           (u/the-id dest-table)
                              :%lower.name        (u/lower-case-en (:dest-column-name fk))
                              :active             true
                              :visibility_type    [:not= "retired"])]
        {:source-field source-field
         :dest-table   dest-table
         :dest-field   dest-field}))))


(mu/defn ^:private mark-fk!
  [database :- i/DatabaseInstance
   table    :- i/TableInstance
   fk       :- i/FKMetadataEntry]
  (when-let [{:keys [source-field dest-table dest-field]} (fetch-fk-relationship-objects database table fk)]
    (log/info (u/format-color 'cyan "Marking foreign key from %s %s -> %s %s"
                (sync-util/name-for-logging table)
                (sync-util/name-for-logging source-field)
                (sync-util/name-for-logging dest-table)
                (sync-util/name-for-logging dest-field)))
    (t2/update! Field (u/the-id source-field)
                {:semantic_type      :type/FK
                 :fk_target_field_id (u/the-id dest-field)})
    true))

(mu/defn sync-fks-for-table!
  "Sync the foreign keys for a specific `table`."
  ([table :- i/TableInstance]
   (sync-fks-for-table! (table/database table) table))

  ([database :- i/DatabaseInstance
    table    :- i/TableInstance]
   (sync-util/with-error-handling (format "Error syncing FKs for %s" (sync-util/name-for-logging table))
     (let [fks-to-update (fetch-metadata/fk-metadata database table)]
       {:total-fks   (count fks-to-update)
        :updated-fks (sync-util/sum-numbers (fn [fk]
                                              (if (mark-fk! database table fk)
                                                1
                                                0))
                                            fks-to-update)}))))

(mu/defn sync-fks!
  "Sync the foreign keys in a `database`. This sets appropriate values for relevant Fields in the Metabase application
  DB based on values from the `FKMetadata` returned by [[metabase.driver/describe-table-fks]]."
  [database :- i/DatabaseInstance]
  (reduce (fn [update-info table]
            (let [table-fk-info (sync-fks-for-table! database table)]
              ;; Mark the table as done with its initial sync once this step is done even if it failed, because only
              ;; sync-aborting errors should be surfaced to the UI (see [[sync-util/exception-classes-not-to-retry]]).
              (sync-util/set-initial-table-sync-complete! table)
              (if (instance? Exception table-fk-info)
                (update update-info :total-failed inc)
                (merge-with + update-info table-fk-info))))
          {:total-fks    0
           :updated-fks  0
           :total-failed 0}
          (sync-util/db->sync-tables database)))
