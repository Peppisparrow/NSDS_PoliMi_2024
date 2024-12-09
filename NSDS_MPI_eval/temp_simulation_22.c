#include <mpi.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

// Group number:22
// Group members:Giuseppe Vitello, Paolo Gennaro, Andrea Giangrande

const double L = 100.0;                 // Length of the 1d domain
const int n = 1000;                     // Total number of points
const int iterations_per_round = 1000;  // Number of iterations for each round of simulation
const double allowed_diff = 0.001;      // Stopping condition: maximum allowed difference between values

double initial_condition(double x, double L) {
    return fabs(x - L / 2);
}

int main(int argc, char **argv) {
    MPI_Init(&argc, &argv);

    int rank, size;
    MPI_Comm_rank(MPI_COMM_WORLD, &rank);
    MPI_Comm_size(MPI_COMM_WORLD, &size);
    double *global_temp_arr = (double *) malloc(sizeof(double) * n);
    if(rank==0){
        for (int i = 0; i < n; i++) {
            global_temp_arr[i] = initial_condition(i * L / (n-1), L);
        }
    }

    double* local_temp_arr = NULL;
    int round = 0;
    while (1) {
        // Perform one round of iterations
        round++;
        local_temp_arr = (double *) malloc(sizeof(double) * n / size);
        for (int t = 0; t < iterations_per_round; t++) {
            // TODO
            int offset = (n*rank/size);
            MPI_Bcast(global_temp_arr, n, MPI_DOUBLE, 0, MPI_COMM_WORLD);
            MPI_Scatter(global_temp_arr, n / size, MPI_DOUBLE, local_temp_arr, n / size, MPI_DOUBLE, 0, MPI_COMM_WORLD);
            for (int i = 0; i < n / size; i++) {
                if (i + offset == 0) {
                    local_temp_arr[i] = (global_temp_arr[i + offset] + global_temp_arr[i + offset + 1]) / 2;
                } else if (i + offset == n - 1) {
                    local_temp_arr[i] = (global_temp_arr[i + offset - 1] + global_temp_arr[i + offset]) / 2;
                } else {
                    local_temp_arr[i] = (global_temp_arr[i + offset - 1] + global_temp_arr[i + offset] + global_temp_arr[i + offset + 1]) / 3;
                }
            }
            MPI_Gather(local_temp_arr, n / size, MPI_DOUBLE, global_temp_arr, n / size, MPI_DOUBLE, 0, MPI_COMM_WORLD);
        }
        double local_min = local_temp_arr[0];
        double local_max = local_temp_arr[0];
        for (int i = 0; i < n / size; i++) {
            if (local_temp_arr[i] < local_min) {
                local_min = local_temp_arr[i];
            }
            if (local_temp_arr[i] > local_max) {
                local_max = local_temp_arr[i];
            }
        }
        free(local_temp_arr);
        // TODO
        // Compute global minimum and maximum
        double global_min, global_max;
        MPI_Allreduce(&local_min, &global_min, 1, MPI_DOUBLE, MPI_MIN, MPI_COMM_WORLD);
        MPI_Allreduce(&local_max, &global_max, 1, MPI_DOUBLE, MPI_MAX, MPI_COMM_WORLD);
        double  max_diff = global_max - global_min;
        if (rank == 0) {

            printf("Round: %d\tMin: %f\tMax: %f\tDiff: %f\n", round, global_min, global_max, max_diff);
        }

        if (max_diff < allowed_diff) {
            break;
        }
        
        // TODO
        // Implement stopping conditions (break)

    }
    free(global_temp_arr);
    // TODO 
    // Deallocation

    MPI_Finalize();
    return 0;
}