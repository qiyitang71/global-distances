import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;


class Transition {
     int state;
     double probability;

    Transition(int state, double probability) {
        this.state = state;
        this.probability = probability;
    }
    public void updateProbability(double probability){
        this.probability = probability;
    }
    public int getState(){
        return this.state;
    }
}

public class Sampling {
    public int numOfStates;
    public int numOfTrans;
    private Map<Integer, List<Transition>> transitions;
    private Map<Integer, List<Transition>> samplingTransitions = new ConcurrentHashMap<>();

    private Map<Integer, Integer> labelMap;
    public double epsilon, delta, delta2;
    PrintStream output0, output1 = null;


//    public Sampling(int numOfStates, Map<Integer, List<Transition>> transitions, int[] labels, double epsilon, double delta, double epsilon2) {
//        this.numOfStates = numOfStates;
//        this.transitions = transitions;
//        this.labels = labels;
//        this.discrepancy = new double[numOfStates * numOfStates];
//        this.epsilon = epsilon;
//        this.delta = delta;
//        this.epsilon2 = epsilon2;
//    }

    public void readFile(String[] args) {
        Scanner input0 = null;
        Scanner input1 = null;

        // parse input file
        if (args.length != 6) {
            System.out.println(
                    "Use java ValueIteration 0: <label> 1:<transition> 2: <outputLabel> 3: <outputTransition> 4: <epsilon> 5: <delta>");
            return;
        }
        // process the command line arguments
        try {
            input0 = new Scanner(new File(args[0]));
        } catch (FileNotFoundException e) {
            System.out.printf("Input file %s not found%n", args[0]);
            System.exit(1);
        }

        try {
            input1 = new Scanner(new File(args[1]));
        } catch (FileNotFoundException e) {
            System.out.printf("Input file %s not found%n", args[1]);
            System.exit(1);
        }

        try {
            output0 = new PrintStream(new File(args[2]));
        } catch (FileNotFoundException e) {
            System.out.printf("Output file %s not created%n", args[2]);
            System.exit(1);
        }

        try {
            output1 = new PrintStream(new File(args[3]));
        } catch (FileNotFoundException e) {
            System.out.printf("Output file %s not created%n", args[3]);
            System.exit(1);
        }

        try {
            this.epsilon = Double.parseDouble(args[4]);
            assert this.epsilon < 1 : String.format("Discount factor %f should be less than 1", this.epsilon);
            assert this.epsilon > 0 : String.format("Discount factor %f should be greater than 0", this.epsilon);
        } catch (NumberFormatException e) {
            System.out.println("Discount factor not provided in the right format");
            System.exit(1);
        }
        try {
            this.delta = Double.parseDouble(args[5]);
            assert this.delta <= 1 : String.format("Accuracy %f should be less than or equal to 1", this.delta);
            assert this.delta > 0 : String.format("Accuracy %f should be greater than 0", this.delta);
        } catch (NumberFormatException e) {
            System.out.println("Accuracy not provided in the right format");
            System.exit(1);
        }

        try {
            this.labelMap = new HashMap<>();
            if (input0.hasNextLine()) input0.nextLine();
            while (input0.hasNextLine()) {
                String line = input0.nextLine();
                String[] nums = line.split(":\\s");
                if (nums.length != 2) {
                    System.err.println(nums[0] + " " + nums[1]);
                    System.err.println("label file format problem");
                }
                int state = Integer.parseInt(nums[0]);
                int label = nums[1].hashCode();
                labelMap.put(state, label);
            }

            this.numOfStates = input1.nextInt();
            this.numOfTrans = input1.nextInt();

            this.transitions = new HashMap<>();

            for (int j = 0; j < numOfTrans; j++) {
                int state = input1.nextInt();
                int next = input1.nextInt();
                double probability = input1.nextDouble();
                transitions.computeIfAbsent(state, x -> new ArrayList<>()).add(new Transition(next, probability));
            }
        } catch (NoSuchElementException e) {
            System.out.printf("Input file %s not in the correct format%n", args[0]);
        }
    }

    public void singleExperiment(int state) {
        List<Transition> list = transitions.get(state);
        int numTran = list.size();
        if (numTran == 1) {
            samplingTransitions.put(state, list);
            return;
        }

        int threads = 12;
        //assume we know the successive states: delta/numTran
        //otherwise: delta/mStoc - mStoc is the number of stochastic states in the system
        long totalCntPerThread = (long) Math.ceil( Math.log(2.0 * numTran/this.delta)/ (this.epsilon * this.epsilon * 2) / threads);
        long totalCnt = totalCntPerThread * threads;
        System.err.println("state: " + state + "/" + numOfStates + ", total cnt = " + totalCnt);

        long[] cntMap = IntStream.range(0, threads)
                .parallel()
                .mapToObj(i -> generateCountMap(list.toArray(Transition[]::new), totalCntPerThread))
                .reduce((map1, map2) -> {
                    long[] map = new long[numOfStates];
                    for (int i = 0; i < numOfStates; i++) {
                        map[i] = map1[i] + map2[i];
                    }
                    return map;
                })
                .orElseThrow();

        List<Transition> newList = new ArrayList<>();
        for (Transition tran : list) {
            int next = tran.state;
            double probability = cntMap[next] * 1.0 / totalCnt;
            newList.add(new Transition(next, probability));
        }
        samplingTransitions.put(state, newList);
    }

    private long[] generateCountMap(Transition[] list, long totalCnt) {
        long[] cntMap = new long[numOfStates];
        Random random = new Random();
        for (long i = 0; i < totalCnt; i++) {
            double rnd = random.nextDouble();
            double sum = 0;
            for (Transition tran : list) {
                int next = tran.state;
                double probability = tran.probability;
                sum += probability;
                if (rnd <= sum) {
                    cntMap[next] = cntMap[next] + 1;
                    break;
                }
            }
        }
        return cntMap;
    }

    public void experiment() {
        for (int i = 0; i < this.numOfStates; i++) {
            singleExperiment(i);
        }
    }

    public void printOutput() {
        System.out.println(this.numOfStates + " " + this.numOfTrans);

        for (int i : this.labelMap.keySet()) {
            System.out.print(i + ": " + this.labelMap.get(i) + " ");
        }
        System.out.println();

        for (int i : this.samplingTransitions.keySet()) {
            List<Transition> list = samplingTransitions.get(i);
            for (Transition tran : list) {
                System.out.println(i + " " + tran.state + " " + tran.probability);
            }
        }
        System.out.println();
    }

    public void printOutputSimple() {
        System.out.println("************ output sampled ************");

        System.out.println(this.numOfStates + " " + this.numOfTrans);

        for (int i : this.labelMap.keySet()) {
            System.out.print(i + ": " + this.labelMap.get(i) + " ");
        }
        System.out.println();

    }

    public void printInput() {
        System.out.println(this.numOfStates + " " + this.numOfTrans);

        for (int i : this.labelMap.keySet()) {
            System.out.print(i + ": " + this.labelMap.get(i) + " ");
        }
        System.out.println();

        for (int i : this.transitions.keySet()) {
            List<Transition> list = transitions.get(i);
            for (Transition tran : list) {
                System.out.println(i + " " + tran.state + " " + tran.probability);
            }
        }
        System.out.println();
    }

    public void printInputSimple() {
        System.out.println("************ input SUL ************");

        System.out.println(this.numOfStates + " " + this.numOfTrans);

        for (int i : this.labelMap.keySet()) {
            System.out.print(i + ": " + this.labelMap.get(i) + " ");
        }
        System.out.println();

    }

    public void writeToFile() {
        output1.println(this.numOfStates + " " + this.numOfTrans);

        output0.println(this.labelMap.values());
        for (int i : this.labelMap.keySet()) {
            output0.println(i + ": " + this.labelMap.get(i));
        }

        for (int i : this.samplingTransitions.keySet()) {
            List<Transition> list = samplingTransitions.get(i);
            for (Transition tran : list) {
                output1.println(i + " " + tran.state + " " + tran.probability);
            }
        }
        output1.println();
    }

    public void smoothTransitions() {
        for (int state : this.samplingTransitions.keySet()) {
            List<Transition> lst = this.samplingTransitions.get(state);
            int len = lst.size();
            double sum = 0;
            for (int i = 0; i < len; i++) {
                if (i == len - 1) {
                    lst.get(i).probability = 1 - sum;
                    break;
                }
                sum += lst.get(i).probability;
            }

        }
    }

    public static void main(String[] args) {
        Sampling sampling = new Sampling();
        sampling.readFile(args);
        sampling.printInputSimple();
        sampling.experiment();
        sampling.printOutputSimple();
        sampling.smoothTransitions();
        sampling.writeToFile();
    }

}
