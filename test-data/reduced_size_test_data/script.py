import pandas as pd

# 1. Define the 2026 Grid Drivers
drivers_2026 = [
    'Lando Norris', 'Oscar Piastri', 'Charles Leclerc', 'Lewis Hamilton',
    'Max Verstappen', 'Isack Hadjar', 'George Russell', 'Kimi Antonelli',
    'Fernando Alonso', 'Lance Stroll', 'Pierre Gasly', 'Franco Colapinto',
    'Esteban Ocon', 'Oliver Bearman', 'Liam Lawson', 'Arvid Lindblad',
    'Carlos Sainz', 'Alexander Albon', 'Nico Hülkenberg', 'Gabriel Bortoleto',
    'Sergio Pérez', 'Valtteri Bottas'
]

def standardize_phone(val):
    """Extracts the last 3 digits and formats as 7700000xxx"""
    if pd.isna(val) or str(val).lower() == 'unknown':
        return "7700000000" # Placeholder for unknown
    s = str(val).strip().replace('+', '')
    # We take the last 3 digits to maintain the ID sequence
    return f"7700000{s[-3:]}"

def process_f1_data(contacts_path, tags_path, ops_path):
    # Load Data
    contacts = pd.read_csv(contacts_path)
    tags = pd.read_csv(tags_path)
    ops = pd.read_csv(ops_path)

    # --- CONTACTS PROCESSING ---
    # Create full name for filtering
    contacts['full_name'] = contacts['forename'] + ' ' + contacts['surname']
    
    # Filter to only 2026 drivers and standardize phone
    contacts_filtered = contacts[contacts['full_name'].isin(drivers_2026)].copy()
    contacts_filtered['phoneNumber'] = contacts_filtered['phoneNumber'].apply(standardize_phone)

    # --- OPERATIONS PROCESSING ---
    # Filter to 2026 drivers and standardize phone
    ops_filtered = ops[ops['driver_name_from_mapping'].isin(drivers_2026)].copy()
    ops_filtered['phone_number'] = ops_filtered['phone_number'].apply(standardize_phone)

    # --- CONSISTENCY CHECK ---
    # Create a lookup dictionary from the contacts file {Name: Phone}
    contact_phone_map = dict(zip(contacts_filtered['full_name'], contacts_filtered['phoneNumber']))
    
    def check_consistency(row):
        expected_phone = contact_phone_map.get(row['driver_name_from_mapping'])
        return row['phone_number'] == expected_phone

    # Apply check
    ops_filtered['is_consistent'] = ops_filtered.apply(check_consistency, axis=1)
    
    mismatches = ops_filtered[ops_filtered['is_consistent'] == False]
    if not mismatches.empty:
        print(f"⚠️ Consistency Alert: Found {len(mismatches)} mismatching phone numbers.")
        print(mismatches[['driver_name_from_mapping', 'phone_number']].drop_duplicates())
    else:
        print("✅ All phone numbers are consistent between contacts and operations.")

    # --- TAGS PROCESSING ---
    # Only keep tags that appear in the filtered operations
    active_tags = set(ops_filtered['tag_name'].unique())
    tags_filtered = tags[tags['name'].isin(active_tags)].copy()

    # --- FINAL CLEANUP & EXPORT ---
    # Drop helper columns and save
    contacts_final = contacts_filtered[['forename', 'surname', 'phoneNumber']]
    ops_final = ops_filtered.drop(columns=['is_consistent'])
    
    contacts_final.to_csv('contacts_2026_clean.csv', index=False)
    ops_final.to_csv('tagging_operations_2026_clean.csv', index=False)
    tags_filtered.to_csv('tags_2026_clean.csv', index=False)
    
    print("\nProcessing complete. Files saved:")
    print("- contacts_2026_clean.csv")
    print("- tagging_operations_2026_clean.csv")
    print("- tags_2026_clean.csv")

# Execute
process_f1_data('contacts.csv', 'tags.csv', 'tagging_operations.csv')
