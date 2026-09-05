export const environment = {
  production: true,
  api: {
    directory: {
      url: 'https://natiart.samuelpetre.com/server/directory',
      endpoints: {
        login: '/login',
        logout: '/signout',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: 'https://natiart.samuelpetre.com/server/product',
      endpoints: {
        category: '/categories',
        directory: '/categories',
        packages: '/packages',
        package: '/packages',
        products: '/products',
        product: '/products',
        order: '/orders',
      }
    },
    viaCep: {
      url: 'https://viacep.com.br/ws',
    },
  }
};
