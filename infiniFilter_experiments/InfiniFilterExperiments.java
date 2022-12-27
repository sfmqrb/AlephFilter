package infiniFilter_experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import filters.BitSacrificer;
import filters.BloomFilter;
import filters.ChainedInfiniFilter;
import filters.CuckooFilter;
import filters.Filter;
import filters.InfiniFilter;
import filters.MultiplyingQF;
import filters.QuotientFilter;

public class InfiniFilterExperiments {

	public static class baseline {
		public Map<String, ArrayList<Double>> metrics;
		public baseline() {
			metrics = new TreeMap<String, ArrayList<Double>>();
			metrics.put("num_entries", new ArrayList<Double>());
			metrics.put("insertion_time", new ArrayList<Double>());
			metrics.put("query_time", new ArrayList<Double>());
			metrics.put("FPR", new ArrayList<Double>());
			metrics.put("memory", new ArrayList<Double>());
			metrics.put("avg_run_length", new ArrayList<Double>());
			metrics.put("avg_cluster_length", new ArrayList<Double>());
		}

		void print(String x_axis_name, String y_axis_name, int commas, int after_commas) {
			ArrayList<Double> x_axis = metrics.get(x_axis_name);
			ArrayList<Double> y_axis = metrics.get(y_axis_name);
			for (int i = 0; i < x_axis.size(); i++) {
				System.out.print(x_axis.get(i));	
				for (int c = 0; c < commas; c++) {
					System.out.print(",");
				}
				System.out.print(y_axis.get(i));	
				for (int c = 0; c < after_commas; c++) {
					System.out.print(",");
				}
				System.out.println();	
			}
		}
		
		void print_to_file(String x_axis_name, String y_axis_name, int commas, int after_commas, FileWriter file) throws IOException {
			ArrayList<Double> x_axis = metrics.get(x_axis_name);
			ArrayList<Double> y_axis = metrics.get(y_axis_name);
			for (int i = 0; i < x_axis.size(); i++) {
				file.write(x_axis.get(i).toString());
				for (int c = 0; c < commas; c++) {
					file.write(",");
				}
				file.write(y_axis.get(i).toString());
				for (int c = 0; c < after_commas; c++) {
					file.write(",");
				}
				file.write("\n");
			}
		}

		double average(String y_axis_name) {
			ArrayList<Double> y_axis = metrics.get(y_axis_name);
			double avg = 0;
			for (int i = 0; i < y_axis.size(); i++) {
				avg += y_axis.get(i);
			}
			return avg / y_axis.size();
		}
	}









	static public void experiment_false_positives() {
		int bits_per_entry = 10;
		int num_entries_power = 5;
		int seed = 5; 
		//int num_entries = (int)Math.pow(2, num_entries_power);
		int fingerprint_size = bits_per_entry - 3;
		QuotientFilter qf = new QuotientFilter(num_entries_power, bits_per_entry);
		HashSet<Integer> added = new HashSet<Integer>();
		Random rand = new Random(seed);
		double load_factor = 0.9;
		int num_queries = 20000;
		int num_false_positives = 0;

		for (int i = 0; i < qf.get_physcial_num_slots() * load_factor; i++) {
			int rand_num = rand.nextInt();
			boolean success = qf.insert(rand_num, false);
			if (success) {
				added.add(rand_num);
			}
			else {
				System.out.println("insertion failed");
			}

		}

		for (int i = 0; i < num_queries; i++) {
			int rand_num = rand.nextInt();
			if (!added.contains(rand_num)) {
				boolean found = qf.search(i);
				if (found) {
					//System.out.println("we seem to have a false positive");
					num_false_positives++;
				}
			}
		}
		double FPR = num_false_positives / (double)num_queries;
		System.out.println("measured FPR:\t" + FPR);
		double expected_FPR = Math.pow(2, - fingerprint_size);
		System.out.println("single fingerprint model:\t" + expected_FPR);
		double expected_FPR_bender = 1 - Math.exp(- load_factor / Math.pow(2, fingerprint_size));
		System.out.println("bender model:\t" + expected_FPR_bender);
	}

	static public void experiment_insertion_speed() {
		int bits_per_entry = 3;
		int num_entries_power = 12;
		int seed = 5; 
		//int num_entries = (int)Math.pow(2, num_entries_power);
		int fingerprint_size = bits_per_entry - 3;
		QuotientFilter qf = new QuotientFilter(num_entries_power, bits_per_entry);
		Random rand = new Random(seed);
		double load_factor = 0.1;
		int num_queries = 20000;
		int num_false_positives = 0;
		double num_insertions = qf.get_physcial_num_slots() * load_factor; 
		long start = System.nanoTime();
		long time_sum = 0;
		long time_sum_square = 0;
		for (int i = 0; i < num_insertions; i++) {
			long start1 = System.nanoTime();
			int rand_num = rand.nextInt();
			boolean success = qf.insert(rand_num, false);

			long end1 = System.nanoTime(); 
			//if (i > 5) {
			long time_diff = (end1 - start1);
			time_sum += time_diff;
			time_sum_square += time_diff * time_diff; 
			//}
			//System.out.println("execution time :\t" + ( end1 - start1) / (1000.0) + " mic s");	
		}
		long end = System.nanoTime(); 
		System.out.println("execution time :\t" + ( end - start) / (1000.0 * 1000.0) + " ms");
		System.out.println("execution time per entry :\t" + ( end - start) / (num_insertions * 1000.0) + " mic sec");

		double avg_nano = time_sum / num_insertions;
		System.out.println("avg :\t" + (avg_nano / 1000.0));

		double avg_normalized = avg_nano / 1000.0;
		double time_sum_square_normalized = time_sum_square / 1000000.0 ;
		double variance = (time_sum_square_normalized - avg_normalized * avg_normalized * num_insertions) / num_insertions;
		double std = Math.sqrt(variance);
		System.out.println("std :\t" + std);
	}


	static public void scalability_experiment(Filter qf, long initial_key, long end_key, baseline results) {

		int num_qeuries = 1000000;
		int query_index = Integer.MAX_VALUE;
		int num_false_positives = 0;

		//int num_entries_to_insert = (int) (Math.pow(2, power) * (qf.expansion_threshold )) - qf.num_existing_entries;
		//final int initial_num_entries = qf.get_num_entries(true);
		
		long initial_num_entries = initial_key;
		long insertion_index = initial_key;
		long start_insertions = System.nanoTime();

		
		//System.out.println("inserting: " + num_entries_to_insert + " to capacity " + Math.pow(2, qf.power_of_two_size));

		boolean successful_insert = false;
		do {
			successful_insert = qf.insert(insertion_index, false);
			insertion_index++;
		} while (insertion_index < end_key && successful_insert);
		
		if (!successful_insert) {
			System.out.println("an insertion failed");
			System.exit(1);
		}
		
		//qf.pretty_print();

		long end_insertions = System.nanoTime();
		long start_queries = System.nanoTime();

		for (int i = 0; i < num_qeuries || num_false_positives < 10; i++) {
			boolean found = qf.search(query_index--);
			if (found) {
				num_false_positives++;
			}
			if (i > num_qeuries * 10) {
				break;
			}
		}
		num_qeuries = Integer.MAX_VALUE - query_index;

		long end_queries = System.nanoTime();
		double avg_insertions = (end_insertions - start_insertions) / (double)(insertion_index - initial_num_entries);
		double avg_queries = (end_queries - start_queries) / (double)num_qeuries;
		double FPR = num_false_positives / (double)num_qeuries;
		//int num_slots = (1 << qf.power_of_two_size) - 1;
		//double utilization = qf.get_utilization();

		//double num_entries = qf.get_num_entries(true);
		double num_entries = qf.get_num_entries(true);

		results.metrics.get("num_entries").add(num_entries);
		results.metrics.get("insertion_time").add(avg_insertions);
		results.metrics.get("query_time").add(avg_queries);
		results.metrics.get("FPR").add(FPR);
		double bits_per_entry = qf.measure_num_bits_per_entry();
		//System.out.println(bits_per_entry);
		results.metrics.get("memory").add(bits_per_entry);

	}

	static public void rejuvenation_experiment(QuotientFilter qf, int power, baseline results, double fraction_queries) {
		int num_qeuries = 1000000;
		int query_index = Integer.MAX_VALUE;
		int num_false_positives = 0;

		//int num_entries_to_insert = (int) (Math.pow(2, power) * (qf.expansion_threshold )) - qf.num_existing_entries;
		final long initial_num_entries = qf.get_num_existing_entries();
		long insertion_index = initial_num_entries;
		Random gen = new Random(initial_num_entries);

		long query_tally = 0;
		
		long start_insertions = System.nanoTime();

		//System.out.println("inserting: " + num_entries_to_insert + " to capacity " + Math.pow(2, qf.power_of_two_size));

		do {
			qf.insert(insertion_index, false);
			insertion_index++;
			if (gen.nextDouble() < fraction_queries) {
				for (int i = 0; i < fraction_queries; i++) {
					//long query_start = System.nanoTime();
					long random_search_key = gen.nextLong() % qf.get_num_existing_entries(); 
					boolean found;
					/*boolean found = qf.search(random_search_key);
					if(!found) {
						System.exit(1);
					}*/
					//long query_end = System.nanoTime();
					//query_tally += query_end - query_start;
					found = qf.rejuvenate(random_search_key);
					if(!found) {
						System.exit(1);
					}
				}
			}
		} while (qf.get_num_existing_entries() < qf.get_max_entries_before_expansion() - 1);
		//qf.pretty_print();

		long end_insertions = System.nanoTime();
		long start_queries = System.nanoTime();

		for (int i = 0; i < num_qeuries; i++) {
			boolean found = qf.search(query_index--);
			if (found) {
				num_false_positives++;
			}
		}

		long end_queries = System.nanoTime();
		double avg_insertions = (end_insertions - start_insertions) / (double)(insertion_index - initial_num_entries);
		double avg_queries = (end_queries - start_queries) / (double)num_qeuries;
		double FPR = num_false_positives / (double)num_qeuries;
		//int num_slots = (1 << qf.power_of_two_size) - 1;
		double utilization = qf.get_utilization();
		double num_entries = qf.get_num_entries(true);

		long totes_insertion = end_insertions - start_insertions;
		//System.out.println("insetion times: " + totes_insertion + "   query tally " + query_tally);
		results.metrics.get("num_entries").add(num_entries);
		results.metrics.get("insertion_time").add(avg_insertions);
		results.metrics.get("query_time").add(avg_queries);
		results.metrics.get("FPR").add(FPR);
		results.metrics.get("memory").add(qf.measure_num_bits_per_entry());
		qf.compute_statistics();
		results.metrics.get("avg_run_length").add(qf.avg_run_length);
		results.metrics.get("avg_cluster_length").add(qf.avg_cluster_length);

	}



	static public void rejuvenation_experiment() {

		int num_cycles = 20;
		int bits_per_entry = 8;
		int num_entries_power = 6;		

		System.gc();

		System.gc();
		{ QuotientFilter qf = new QuotientFilter(num_entries_power, bits_per_entry);
			scalability_experiment(qf, 0, qf.get_max_entries_before_expansion() - 1, new baseline());}
		{ QuotientFilter qf = new QuotientFilter(num_entries_power, bits_per_entry);
			scalability_experiment(qf, 0, qf.get_max_entries_before_expansion() - 1, new baseline());}
		//orig.expand();
		//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));

		baseline original_qf_res = new baseline();
		for (int i = num_entries_power; i < num_cycles; i++ ) {
			QuotientFilter orig = new QuotientFilter(i, bits_per_entry);
			orig.set_expand_autonomously(true); 
			//scalability_experiment(orig, i, original_qf_res);
			//orig.expand();
			//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
		}


		System.gc();

		baseline bit_sacrifice_res = new baseline();
		{
			BitSacrificer qf2 = new BitSacrificer(num_entries_power, bits_per_entry);
			qf2.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles && qf2.get_fingerprint_length() > 0; i++ ) {
				//scalability_experiment(qf2, i, bit_sacrifice_res);
				//qf2.expand();
			}
		}


		System.gc();
		baseline chained_IF_1 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF_1, 0);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	

		System.gc();
		baseline chained_IF2 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF2, 0.2);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	

		System.gc();
		baseline chained_IF_3 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF_3, 1);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	

		System.gc();
		
		baseline chained_IF_4 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF_4, 2);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	

		System.gc();

		baseline geometric_expansion_res = new baseline();
		{
			MultiplyingQF qf3 = new MultiplyingQF(num_entries_power, bits_per_entry);
			qf3.set_expand_autonomously(true);
			for (int i = num_entries_power; i < num_cycles - 1; i++ ) {
				//scalability_experiment(qf3, i, geometric_expansion_res);
				//System.out.println("# entries: " + qf3.num_existing_entries + " new capacity: " + Math.pow(2, qf3.power_of_two_size + 1));
				//qf3.expand();
			}
		}
		//scalability_experiment(qf3);


		int commas_before = 1;
		int commas_after = 6;
		original_qf_res.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "insertion_time", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "insertion_time", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "insertion_time", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "query_time", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "query_time", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "query_time", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "FPR", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "FPR", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "FPR", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "memory", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "memory", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "memory", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "avg_run_length", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "avg_run_length", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		chained_IF_3.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		chained_IF_4.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "avg_cluster_length", commas_before++, commas_after--);

	}
	
}

	
	/*static public void increasing_memory_experiment() {

		int num_cycles = 20;
		int bits_per_entry = 8;
		int num_entries_power = 6;		

		System.gc();

		scalability_experiment(new QuotientFilter(num_entries_power, bits_per_entry), num_entries_power, new baseline());
		scalability_experiment(new QuotientFilter(num_entries_power, bits_per_entry), num_entries_power, new baseline());

		//orig.expand();
		//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));

		baseline original_qf_res = new baseline();
		for (int i = num_entries_power; i < num_cycles; i++ ) {
			QuotientFilter orig = new QuotientFilter(i, bits_per_entry);
			orig.expand_autonomously = true; 
			scalability_experiment(orig, i, original_qf_res);
			//orig.expand();
			//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
		}


		System.gc();

		baseline bit_sacrifice_res = new baseline();
		{
			BitSacrificer qf2 = new BitSacrificer(num_entries_power, bits_per_entry);
			qf2.expand_autonomously = true;
			
			for (int i = num_entries_power; i < num_cycles && qf2.fingerprintLength > 0; i++ ) {
				scalability_experiment(qf2, i, bit_sacrifice_res);
				//qf2.expand();
			}
		}


		System.gc();
		baseline chained_IF_1 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.expand_autonomously = true; 
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF_1, 0);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	

		System.gc();
		baseline chained_IF2 = new baseline();
		{
			InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
			qf.expand_autonomously = true; 
			for (int i = num_entries_power; i < num_cycles; i++ ) {
				rejuvenation_experiment(qf, i, chained_IF2, 0.2);
				//qf.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}
			//qf.print_filter_summary();
			//System.out.println("Niv: " + qf.older_filters.size());
		}	



		System.gc();

		baseline geometric_expansion_res = new baseline();
		{
			MultiplyingQF qf3 = new MultiplyingQF(num_entries_power, bits_per_entry);
			qf3.expand_autonomously = true;
			for (int i = num_entries_power; i < num_cycles - 1; i++ ) {
				scalability_experiment(qf3, i, geometric_expansion_res);
				//System.out.println("# entries: " + qf3.num_existing_entries + " new capacity: " + Math.pow(2, qf3.power_of_two_size + 1));
				//qf3.expand();
			}
		}
		//scalability_experiment(qf3);


		int commas_before = 1;
		int commas_after = 6;
		original_qf_res.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "insertion_time", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "insertion_time", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "insertion_time", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "insertion_time", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "query_time", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "query_time", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "query_time", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "query_time", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "FPR", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "FPR", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "FPR", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "FPR", commas_before++, commas_after--);

		System.out.println();

		commas_before = 1;
		commas_after = 6;
		original_qf_res.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF_1.print("num_entries", "memory", commas_before++, commas_after--);
		chained_IF2.print("num_entries", "memory", commas_before++, commas_after--);
		bit_sacrifice_res.print("num_entries", "memory", commas_before++, commas_after--);
		geometric_expansion_res.print("num_entries", "memory", commas_before++, commas_after--);
	}*/
	
	
	/*static public void memory_experiment() {
		int num_entries_power = 6;		

		for (int bits_per_entry = 8; bits_per_entry <= 16; bits_per_entry++) {

			int num_cycles = bits_per_entry + num_entries_power;

			System.gc();

			scalability_experiment(new QuotientFilter(num_entries_power, bits_per_entry), num_entries_power, new baseline());
			scalability_experiment(new QuotientFilter(num_entries_power, bits_per_entry), num_entries_power, new baseline());

			//orig.expand();
			//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));

			baseline original_qf_res = new baseline();
			for (int i = num_entries_power; i <  num_cycles; i++ ) {
				QuotientFilter orig = new QuotientFilter(i, bits_per_entry);
				orig.expand_autonomously = true; 
				scalability_experiment(orig, i, original_qf_res);
				//orig.expand();
				//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
			}

			System.gc();

			baseline bit_sacrifice_res = new baseline();
			{
				BitSacrificer qf2 = new BitSacrificer(num_entries_power, bits_per_entry);
				qf2.expand_autonomously = true;
				for (int i = num_entries_power; i < num_cycles && qf2.fingerprintLength > 0; i++ ) {
					scalability_experiment(qf2, i, bit_sacrifice_res);
					//qf2.expand();
				}
			}


			System.gc();
			baseline chained_IF_res = new baseline();
			{
				InfiniFilter qf = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
				qf.expand_autonomously = true; 
				for (int i = num_entries_power; i < num_cycles; i++ ) {
					scalability_experiment(qf, i, chained_IF_res);
					//qf.expand();
					//System.out.println("# entries: " + qf.num_existing_entries + " new capacity: " + Math.pow(2, qf.power_of_two_size));
				}
				//qf.print_filter_summary();
				//System.out.println("Niv: " + qf.older_filters.size());
			}	

			System.gc();

			baseline geometric_expansion_res = new baseline();
			{
				MultiplyingQF qf3 = new MultiplyingQF(num_entries_power, bits_per_entry);
				qf3.expand_autonomously = true;
				for (int i = num_entries_power; i < num_cycles - 1; i++ ) {
					scalability_experiment(qf3, i, geometric_expansion_res);
					//System.out.println("# entries: " + qf3.num_existing_entries + " new capacity: " + Math.pow(2, qf3.power_of_two_size + 1));
					//qf3.expand();
				}
			}
			//scalability_experiment(qf3);

			String metric = "memory";
			System.out.print( bits_per_entry 	+ "," );
			System.out.print( original_qf_res.average(metric) 	+ "," );
			System.out.print( chained_IF_res.average(metric) 	+ "," );
			System.out.print( bit_sacrifice_res.average(metric) + "," );
			System.out.print( geometric_expansion_res.average(metric)  );

			System.out.println();

		}
	}*/

	/*static public void increasing_fingerprint_sizes_experiment() {

		int num_cycles = 20;
		int bits_per_entry = 8;
		int num_entries_power = 6;		

		InfiniFilter qf1 = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
		qf1.fprStyle = FingerprintGrowthStrategy.FalsePositiveRateExpansion.UNIFORM;
		qf1.expand_autonomously = true; 
		baseline res1 = new baseline();
		for (int i = num_entries_power; i < num_cycles; i++ ) {
			scalability_experiment(qf1, i, res1);
		}
		
		InfiniFilter qf2 = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
		qf2.fprStyle = FingerprintGrowthStrategy.FalsePositiveRateExpansion.POLYNOMIAL;
		qf2.expand_autonomously = true; 
		baseline res2 = new baseline();
		for (int i = num_entries_power; i < num_cycles; i++ ) {
			scalability_experiment(qf2, i, res2);
		}


		InfiniFilter qf3 = new ChainedInfiniFilter(num_entries_power, bits_per_entry);
		qf3.fprStyle = FingerprintGrowthStrategy.FalsePositiveRateExpansion.GEOMETRIC;
		qf3.expand_autonomously = true;
		baseline res3 = new baseline();
		for (int i = num_entries_power; i < num_cycles; i++ ) {
			//scalability_experiment(qf3, i, res3);
		}
		
		MultiplyingQF qf4 = new MultiplyingQF(num_entries_power, bits_per_entry);
		qf4.sizeStyle = SizeExpansion.GEOMETRIC;
		qf4.fprStyle = FingerprintGrowthStrategy.FalsePositiveRateExpansion.POLYNOMIAL;
		qf4.expand_autonomously = true;
		baseline res4 = new baseline();
		for (int i = num_entries_power; i < num_cycles - 1; i++ ) {
			scalability_experiment(qf4, i, res4);
		}

		

		res1.print("num_entries", "insertion_time", 1, 2);
		res2.print("num_entries", "insertion_time", 2, 1);
		res4.print("num_entries", "insertion_time", 3, 0);

		System.out.println();

		res1.print("num_entries", "query_time", 1, 2);
		res2.print("num_entries", "query_time", 2, 1);
		res4.print("num_entries", "query_time", 3, 0);

		System.out.println();

		res1.print("num_entries", "FPR", 1, 2);
		res2.print("num_entries", "FPR", 2, 1);
		res4.print("num_entries", "FPR", 3, 0);

		System.out.println();

		res1.print("num_entries", "memory", 1, 2);
		res2.print("num_entries", "memory", 2, 1);
		res4.print("num_entries", "memory", 3, 0);
		
		/*System.out.println();
		qf1.print_levels();
		System.out.println();
		qf2.print_levels();
		System.out.println();
		qf3.print_levels();*/
		
		/*System.out.println("--------------------------------------------");
		qf1.print_filter_summary();
		System.out.println("--------------------------------------------");
		qf2.print_filter_summary();
		System.out.println("--------------------------------------------");
		qf3.print_filter_summary();
		
	}*/
	
	


