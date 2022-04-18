import java.util.BitSet;

public class FingerprintShrinkingQF extends QuotientFilter {

	FingerprintShrinkingQF(int power_of_two, int bits_per_entry) {
		super(power_of_two, bits_per_entry);
		// TODO Auto-generated constructor stub
		max_entries_before_expansion = (int)(Math.pow(2, power_of_two_size) * expansion_threshold);
	}
	
	void expand() {
		QuotientFilter new_qf = new QuotientFilter(power_of_two_size + 1, bitPerEntry - 1);
		num_extension_slots = (power_of_two_size + 1) * 2;
		Iterator it = new Iterator(this);
		
		while (it.next()) {
			int bucket = it.bucket_index;
			long fingerprint = it.fingerprint;
			long pivot_bit = (1 & fingerprint);
			long bucket_mask = pivot_bit << power_of_two_size;
			long updated_bucket = bucket | bucket_mask;
			long updated_fingerprint = fingerprint >> 1;
			
			/*System.out.println(bucket); 
			System.out.print("bucket1      : ");
			print_int_in_binary( bucket, power_of_two_size);
			System.out.print("fingerprint1 : ");
			print_int_in_binary((int) fingerprint, fingerprintLength);
			System.out.print("pivot        : ");
			print_int_in_binary((int) pivot_bit, 1);
			System.out.print("bucket2      : ");
			print_int_in_binary((int) updated_bucket, power_of_two_size + 1);
			System.out.print("fingerprint2 : ");
			print_int_in_binary((int) updated_fingerprint, fingerprintLength - 1);
			System.out.println();
			System.out.println();*/
			
			new_qf.insert(updated_fingerprint, (int)updated_bucket, false);
		}
		
		filter = new_qf.filter;
		power_of_two_size++;
		num_extension_slots += 2;
		bitPerEntry--;
		fingerprintLength--;
		max_entries_before_expansion = (int)(Math.pow(2, power_of_two_size) * expansion_threshold);

	}
	
}
