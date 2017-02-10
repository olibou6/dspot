package fr.inria.diversify.dspot.selector;

import fr.inria.diversify.dspot.AmplificationChecker;
import fr.inria.diversify.dspot.AmplificationHelper;
import fr.inria.diversify.dspot.support.Counter;
import fr.inria.diversify.mutant.pit.PitResult;
import fr.inria.diversify.mutant.pit.PitResultParser;
import fr.inria.diversify.mutant.pit.PitRunner;
import fr.inria.diversify.runner.InputConfiguration;
import fr.inria.diversify.runner.InputProgram;
import fr.inria.diversify.util.Log;
import fr.inria.diversify.util.PrintClassUtils;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 1/5/17
 */
public class PitMutantScoreSelector implements TestSelector {

    private int numberOfMutant;

    private InputProgram program;

    private InputConfiguration configuration;

    private CtType currentClassTestToBeAmplified;

    private List<PitResult> originalKilledMutants;

    private Map<CtMethod, Set<PitResult>> testThatKilledMutants;

    private List<PitResult> mutantNotTestedByOriginal;

    public PitMutantScoreSelector() {
        this.testThatKilledMutants = new HashMap<>();
    }

    public PitMutantScoreSelector(String pathToOriginalResultOfPit) {
        this();
        initOriginalPitResult(PitResultParser.parse(new File(pathToOriginalResultOfPit)));
    }

    @Override
    public void init(InputConfiguration configuration) {
        this.configuration = configuration;
        this.program = this.configuration.getInputProgram();
        if (this.originalKilledMutants == null) {
            initOriginalPitResult(PitRunner.runAll(this.program, this.configuration));
        }
    }

    private void initOriginalPitResult(List<PitResult> results) {
        this.numberOfMutant = results.size();
        this.mutantNotTestedByOriginal = results.stream()
                .filter(result -> result.getStateOfMutant() != PitResult.State.KILLED)
                .filter(result -> result.getStateOfMutant() != PitResult.State.SURVIVED)
                .filter(result -> result.getStateOfMutant() != PitResult.State.NO_COVERAGE)
                .collect(Collectors.toList());
        this.originalKilledMutants = results.stream()
                .filter(result -> result.getStateOfMutant() == PitResult.State.KILLED)
                .collect(Collectors.toList());
        Log.debug("The original test suite kill {} / {}", this.originalKilledMutants.size(), results.size());
    }

    @Override
    public void reset() {
        //empty
    }

    @Override
    public List<CtMethod<?>> selectToAmplify(List<CtMethod<?>> testsToBeAmplified) {
        if (this.currentClassTestToBeAmplified == null && !testsToBeAmplified.isEmpty()) {
            this.currentClassTestToBeAmplified = testsToBeAmplified.get(0).getDeclaringType();
        }
        return testsToBeAmplified;
    }

    @Override
    public List<CtMethod<?>> selectToKeep(List<CtMethod<?>> amplifiedTestToBeKept) {
        if (amplifiedTestToBeKept.isEmpty()) {
            return amplifiedTestToBeKept;
        }
        CtType clone = this.currentClassTestToBeAmplified.clone();
        clone.setParent(this.currentClassTestToBeAmplified.getParent());
        ((Set<CtMethod>) this.currentClassTestToBeAmplified.getMethods()).stream()
                .filter(AmplificationChecker::isTest)
                .forEach(clone::removeMethod);
        amplifiedTestToBeKept.forEach(clone::addMethod);

        try {
            PrintClassUtils.printJavaFile(new File(this.program.getAbsoluteTestSourceCodeDir()), clone);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<PitResult> results = PitRunner.run(this.program, this.configuration, clone);
        Set<CtMethod<?>> selectedTests = new HashSet<>();
        if (results != null) {
            Log.debug("{} mutants has been generated ({})", results.size(), this.numberOfMutant);
            if (results.size() != this.numberOfMutant) {
                Log.warn("Number of generated mutant is different than the original one.");
            }
            results.stream()
                    .filter(result -> result.getStateOfMutant() == PitResult.State.KILLED)
                    .filter(result -> !this.originalKilledMutants.contains(result))
                    .filter(result -> !this.mutantNotTestedByOriginal.contains(result))
                    .forEach(result -> {
                        CtMethod method = result.getMethod(clone);
                        if (killsMoreMutantThanParents(method, result)) {
                            if (!testThatKilledMutants.containsKey(method)) {
                                testThatKilledMutants.put(method, new HashSet<>());
                            }
                            testThatKilledMutants.get(method).add(result);
                            selectedTests.add(method);
                        }
                    });
        }

        selectedTests.forEach(selectedTest ->
                Log.debug("{} kills {} more mutants", selectedTest.getSimpleName(), testThatKilledMutants.get(selectedTest).size())
        );

        try {
            PrintClassUtils.printJavaFile(new File(this.program.getAbsoluteTestSourceCodeDir()), this.currentClassTestToBeAmplified);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new ArrayList<>(selectedTests);
    }

    private boolean killsMoreMutantThanParents(CtMethod test, PitResult result) {
        CtMethod parent = AmplificationHelper.getAmpTestToParent().get(test);
        while (parent != null) {
            if (this.testThatKilledMutants.get(parent) != null &&
                    this.testThatKilledMutants.get(parent).contains(result)) {
                return false;
            }
            parent = AmplificationHelper.getAmpTestToParent().get(parent);
        }
        return true;
    }

    @Override
    public void update() {
        // empty
    }

    @Override
    public void report() {
        reportStdout();
        reportJSONMutants();
        //clean up for the next class
        this.currentClassTestToBeAmplified = null;
    }

    @Override
    public int getNbAmplifiedTestCase() {
        return this.testThatKilledMutants.size();
    }

    @Override
    public CtType buildClassForSelection(CtType original, List<CtMethod<?>> methods) {
        CtType clone = original.clone();
        original.getPackage().addType(clone);
        methods.forEach(clone::addMethod);
        return clone;
    }

    private void reportStdout() {
        final StringBuilder string = new StringBuilder();
        final String nl = System.getProperty("line.separator");
        long nbOfTotalMutantKilled = getNbTotalNewMutantKilled();
        string.append(nl).append("======= REPORT =======").append(nl);
        string.append("PitMutantScoreSelector: ").append(nl);
        string.append("The original test suite kill ").append(this.originalKilledMutants.size()).append(" mutants").append(nl);
        string.append("The amplification results with ").append(
                this.testThatKilledMutants.size()).append(" new tests").append(nl);
        string.append("it kill ")
                .append(nbOfTotalMutantKilled).append(" more mutants").append(nl);
        System.out.println(string.toString());

        File reportDir = new File(this.configuration.getOutputDirectory());
        if (!reportDir.exists()) {
            reportDir.mkdir();
        }
        try (FileWriter writer = new FileWriter(this.configuration.getOutputDirectory() + "/" +
                this.currentClassTestToBeAmplified.getQualifiedName() + "_mutants_report.txt", false)) {
            writer.write(string.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long getNbTotalNewMutantKilled() {
        return this.testThatKilledMutants.keySet()
                .stream()
                .flatMap(method -> this.testThatKilledMutants.get(method).stream())
                .map(PitResult::getFullQualifiedNameMutantOperator)
                .distinct()
                .count();
    }

    @Deprecated
    private double fitness(CtMethod test) {
        return ((double) this.testThatKilledMutants.get(test).size() / (double) (Counter.getAssertionOfSinceOrigin(test) + Counter.getInputOfSinceOrigin(test)));
    }

    private static final String nl = System.getProperty("line.separator");
    private static final String tab = "\t";

    private void reportJSONMutants() {
        try (FileWriter writer = new FileWriter(this.configuration.getOutputDirectory() + "/" +
                this.currentClassTestToBeAmplified.getQualifiedName() + "_mutants_killed.json", false)) {
            writer.write("{" + nl);
            writer.write(tab + "\"" +
                    this.currentClassTestToBeAmplified.getQualifiedName() + "\": {" + nl);
            List<CtMethod> keys = new ArrayList<>(this.testThatKilledMutants.keySet());
            keys.forEach(amplifiedTest -> {
                        final StringBuilder string = new StringBuilder();
                        string.append(tab).append(tab)
                                .append("\"").append(amplifiedTest.getSimpleName()).append("\":{").append(nl)
                                .append(tab).append(tab).append(tab)
                                .append("\"#AssertionAdded\":").append(Counter.getAssertionOfSinceOrigin(amplifiedTest)).append(",").append(nl)
                                .append(tab).append(tab).append(tab)
                                .append("\"#InputAdded\":").append(Counter.getInputOfSinceOrigin(amplifiedTest)).append(",").append(nl)
                                .append(tab).append(tab).append(tab)
                                .append("\"#MutantKilled\":").append(this.testThatKilledMutants.get(amplifiedTest).size()).append(",").append(nl)
                                .append(tab).append(tab).append(tab)
                                .append("\"MutantsKilled\":[").append(nl)
                                .append(buildListOfIdMutantKilled(amplifiedTest))
                                .append(tab).append(tab).append(tab).append("]").append(nl)
                                .append(tab).append(tab).append("}");
                        if (keys.indexOf(amplifiedTest) != keys.size() - 1)
                            string.append(",");
                        string.append(nl);
                        try {
                            writer.write(string.toString());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
            writer.write(nl + tab + "}");
            writer.write(nl + "}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private StringBuilder buildListOfIdMutantKilled(CtMethod amplifiedTest) {
        List<PitResult> pitResults = new ArrayList<>(this.testThatKilledMutants.get(amplifiedTest));
        return pitResults.stream()
                .reduce(new StringBuilder(),
                        (builder, element) -> {
                            if (pitResults.indexOf(element) ==
                                    pitResults.size() - 1) {
                                return builder.append(getLineOfMutantKilled(element));
                            } else {
                                return builder.append(getLineOfMutantKilled(element)).append(",").append(nl);
                            }
                        }, StringBuilder::append);
    }

    private StringBuilder getLineOfMutantKilled(PitResult element) {
        return new StringBuilder().append(tab).append(tab).append(tab).append("{").append(nl)
                .append(tab).append(tab).append(tab).append(tab)
                .append("\"ID\":").append("\"").append(element.getFullQualifiedNameMutantOperator()).append("\",").append(nl)
                .append(tab).append(tab).append(tab).append(tab)
                .append("\"lineNumber\":\"").append(element.getLineNumber()).append("\",").append(nl)
                .append(tab).append(tab).append(tab).append(tab)
                .append("\"location\":\"").append(element.getLocation()).append("\"").append(nl)
                .append(tab).append(tab).append(tab).append("}").append(nl);
    }
}